/**
 * One collector for state Public Service Commissions.
 * Registry rows live in data/sources/registry.json — no psc.json catalog.
 */
const { fetchText, sleep } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const {
  parseCalendarHtml,
  looksBlocked,
  isFullIsoDate,
  officialUrlForRow,
} = require('../lib/calendarPdf');

const NOTICE_RE =
  /notif|advert|advt|recruit|vacanc|exam|apply|calendar|corrigend|advertisement|commission|pcs|cce|combined/i;
const SKIP_RE =
  /login|result only|answer key|syllabus pdf|facebook|twitter|youtube|instagram|tender|rti act/i;

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

function isPscNoticeLink(link) {
  const blob = `${link.title || ''} ${link.href || ''}`;
  if (SKIP_RE.test(blob)) return false;
  return NOTICE_RE.test(blob);
}

function toPscRecord(item, source, meta, extras = {}) {
  const rec = toStagingRecord(
    {
      organization: source.name || item.organization,
      orgType: source.orgTypeDefault || 'autonomous',
      location: extras.location || source.locationDefault || 'State',
      hasExam: true,
      selectionProcess: extras.selectionProcess || 'written_multi_stage',
      extraText: extras.extraText || 'written examination multi stage state PSC',
      eligibility: ['Eligibility must be verified on the official site.'],
      summary:
        extras.summary ||
        `${item.title || 'State PSC examination'}. Eligibility must be verified on the official site.`,
      ...item,
      hasExam: true,
      selectionProcess: extras.selectionProcess || 'written_multi_stage',
    },
    source,
    meta
  );
  rec.eligibilityParse = { complete: false };
  return rec;
}

function calendarToRecord(row, source, listUrl, collectedAt) {
  const officialUrl = officialUrlForRow(row, listUrl);
  return toPscRecord(
    {
      title: row.name,
      href: officialUrl,
      officialUrl,
      sourceUrl: listUrl,
      notificationDate: isFullIsoDate(row.notificationDate) ? row.notificationDate : null,
      lastDate: isFullIsoDate(row.lastDate) ? row.lastDate : null,
    },
    source,
    { collectedAt, collectorVersion: 'calendar-v1', listUrl },
    {
      extraText: 'written examination multi stage state PSC calendar',
      summary: [row.name, row.lastDate && `Last date: ${row.lastDate}`, 'Verify on the official site.']
        .filter(Boolean)
        .join(' '),
    }
  );
}

function parsePscPage(html, pageUrl) {
  const calendarRows = parseCalendarHtml(html, pageUrl);
  const links = extractLinks(html, pageUrl, { jobLikeOnly: false, limit: 80 }).filter(isPscNoticeLink);
  return { calendarRows, links, parsed: calendarRows.length + links.length > 0 };
}

async function collectGenericPsc(source, ctx) {
  const { runId, collectedAt } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source);
  const delayMs = Number(source.rateLimitMs || 800);
  const records = [];
  let pages = 0;
  let parsedOk = 0;
  let calendarRows = 0;
  let linksFound = 0;

  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl, { delayMs, retries: 1, timeoutMs: 20000 });
      const rawName = `psc-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`;
      saveRaw(source.sourceId, runId, rawName, text);
      pages += 1;

      if (looksBlocked(text)) {
        errors.push({ url, message: 'blocked or CAPTCHA page; not solved — use paste-URL' });
        continue;
      }

      const parsed = parsePscPage(text, url);
      if (parsed.parsed) parsedOk += 1;
      calendarRows += parsed.calendarRows.length;
      linksFound += parsed.links.length;

      for (const row of parsed.calendarRows) {
        records.push(calendarToRecord(row, source, url, collectedAt));
      }
      for (const link of parsed.links) {
        records.push(
          toPscRecord({ ...link, sourceUrl: url }, source, {
            collectedAt,
            collectorVersion: 'scrape-v1',
            listUrl: url,
          })
        );
      }
    } catch (err) {
      errors.push({
        url: listUrl,
        message: err.message || String(err),
      });
    }
    await sleep(Math.min(delayMs, 400));
  }

  if (pages > 0 && parsedOk === 0) {
    errors.push({
      url: source.baseUrl || listUrls[0] || '',
      message: 'listUrls did not parse a notice list — leave disabled or use ops paste-URL',
    });
  }

  const unique = dedupeByUrl(records);
  return {
    records: unique,
    errors,
    metrics: {
      listUrls: listUrls.length,
      pages,
      parsedOk,
      calendarRows,
      links: linksFound,
      written: unique.length,
    },
  };
}

module.exports = { collectGenericPsc, parsePscPage, isPscNoticeLink, candidateUrls };
