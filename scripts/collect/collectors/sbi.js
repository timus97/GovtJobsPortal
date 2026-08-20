const cheerio = require('cheerio');
const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks, absoluteUrl } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { looksBlocked } = require('../lib/calendarPdf');
const { parseDateFromText } = require('../lib/dates');

function candidateUrls(source) {
  const urls = [];
  if (source.listUrls?.length) urls.push(...source.listUrls);
  if (source.baseUrl) urls.push(source.baseUrl);
  const seen = new Set();
  return urls.filter((u) => {
    if (!u || seen.has(u)) return false;
    seen.add(u);
    return true;
  });
}

function guessSelectionProcess(title) {
  const blob = String(title || '');
  if (/apprentice/i.test(blob)) return 'apprenticeship';
  if (/retired|contract basis|interview schedule/i.test(blob) && !/written|exam|cbt|po\b|junior associate/i.test(blob)) {
    return 'contract_interview';
  }
  if (/written|cbt|junior associate|probationary|po\b/i.test(blob)) return 'cbt';
  return 'cbt';
}

function openingHasExam(title, process) {
  if (process === 'apprenticeship') return true;
  if (process === 'contract_interview') return false;
  return true;
}

function parseSbiOpenings(html, pageUrl) {
  const $ = cheerio.load(html || '');
  const items = [];
  const seen = new Set();

  const blocks = $('div, li, article, section, tr').toArray();
  for (const el of blocks) {
    const $el = $(el);
    const text = $el.text().replace(/\s+/g, ' ').trim();
    if (!/advertisement\s*no/i.test(text)) continue;
    if (text.length > 1200) continue;

    const adv = text.match(/advertisement\s*no[:\s.]*([A-Z0-9/._-]+)/i);
    const last = text.match(/last\s*date(?:\s*to\s*apply)?[:\s]*([0-9]{1,2}[-/.][0-9]{1,2}[-/.]20[0-9]{2})/i);
    let title = text
      .replace(/advertisement\s*no[:\s.]*[A-Z0-9/._-]+/i, ' ')
      .replace(/last\s*date(?:\s*to\s*apply)?[:\s]*[0-9]{1,2}[-/.][0-9]{1,2}[-/.]20[0-9]{2}/i, ' ')
      .replace(/apply now/i, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    if (title.length > 220) title = title.slice(0, 220).trim();
    if (!title || title.length < 8) continue;

    const applyHref = $el.find('a[href]').filter((_, a) => /apply/i.test($(a).text())).first().attr('href');
    const anyHref = $el.find('a[href]').first().attr('href');
    const officialUrl = absoluteUrl(pageUrl, applyHref || anyHref) || pageUrl;
    const key = `${title}|${officialUrl}`;
    if (seen.has(key)) continue;
    seen.add(key);

    items.push({
      title,
      advertisementNo: adv ? adv[1] : null,
      lastDate: last ? parseDateFromText(last[1]) : parseDateFromText(text),
      officialUrl,
      href: officialUrl,
    });
  }

  if (items.length === 0) {
    const links = extractLinks(html, pageUrl, { jobLikeOnly: true, limit: 40 }).filter((l) =>
      /recruit|apply|opening|crpd|advert/i.test(`${l.title} ${l.href}`)
    );
    for (const link of links) {
      if (seen.has(link.href)) continue;
      seen.add(link.href);
      items.push({
        title: link.title,
        advertisementNo: null,
        lastDate: parseDateFromText(link.title),
        officialUrl: link.href,
        href: link.href,
      });
    }
  }

  return items;
}

function toSbiRecord(item, source, meta) {
  const selectionProcess = guessSelectionProcess(item.title);
  const rec = toStagingRecord(
    {
      organization: 'State Bank of India',
      orgType: source.orgTypeDefault || 'central',
      location: 'All India',
      title: item.title,
      officialUrl: item.officialUrl,
      href: item.href,
      lastDate: item.lastDate,
      notificationNo: item.advertisementNo || null,
      sourceUrl: meta.listUrl,
      hasExam: openingHasExam(item.title, selectionProcess),
      selectionProcess,
      extraText: openingHasExam(item.title, selectionProcess)
        ? 'computer based test SBI recruitment'
        : 'interview contract SBI',
      eligibility: ['Eligibility must be verified on the official site.'],
      summary: `${item.title}${item.advertisementNo ? ` (${item.advertisementNo})` : ''}. Eligibility must be verified on the official site.`,
    },
    source,
    meta
  );
  rec.eligibilityParse = { complete: false };
  return rec;
}

async function collectSbi(source, ctx) {
  const { runId, collectedAt } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source);
  const delayMs = Number(source.rateLimitMs || 700);
  const records = [];
  let pages = 0;
  let parsedOpenings = 0;

  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl, { delayMs, retries: 1, timeoutMs: 20000 });
      const rawName = `list-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`;
      saveRaw(source.sourceId, runId, rawName, text);
      pages += 1;

      if (looksBlocked(text)) {
        errors.push({ url, message: 'blocked or CAPTCHA page; not solved' });
        continue;
      }

      const openings = parseSbiOpenings(text, url);
      parsedOpenings += openings.length;
      for (const item of openings) {
        records.push(
          toSbiRecord(item, source, {
            collectedAt,
            collectorVersion: 'scrape-v1',
            listUrl: url,
          })
        );
      }
    } catch (err) {
      const msg = err.message || String(err);
      errors.push({
        url: listUrl,
        message: /HTTP 403|HTTP 429|captcha|blocked/i.test(msg) ? `blocked: ${msg}` : msg,
      });
    }
  }

  const unique = dedupeByUrl(records);
  return {
    records: unique,
    errors,
    metrics: {
      listUrls: listUrls.length,
      pages,
      openings: parsedOpenings,
      written: unique.length,
    },
  };
}

module.exports = { collectSbi, parseSbiOpenings, guessSelectionProcess };
