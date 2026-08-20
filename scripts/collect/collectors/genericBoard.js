/**
 * P1 board / regulator / post / institute list pages.
 * Metadata + official URLs only. No CAPTCHA bypass.
 */
const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { looksBlocked } = require('../lib/calendarPdf');

const NOTICE_RE =
  /recruit|vacanc|notif|advert|advt|opening|career|apply|engagement|appointment|vacancy/i;
const SKIP_RE = /login|captcha|facebook|twitter|youtube|tender only|rti/i;

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

function isBoardNoticeLink(link) {
  const blob = `${link.title || ''} ${link.href || ''}`;
  if (SKIP_RE.test(blob)) return false;
  return NOTICE_RE.test(blob);
}

function parseBoardPage(html, pageUrl) {
  const links = extractLinks(html, pageUrl, { jobLikeOnly: false, limit: 80 }).filter(isBoardNoticeLink);
  return { links, parsed: links.length > 0 };
}

async function collectGenericBoard(source, ctx) {
  const { runId, collectedAt } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source);
  const delayMs = Number(source.rateLimitMs || 800);
  const records = [];
  let pages = 0;
  let parsedOk = 0;
  let linksFound = 0;

  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl, { delayMs, retries: 1, timeoutMs: 20000 });
      saveRaw(
        source.sourceId,
        runId,
        `board-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`,
        text
      );
      pages += 1;
      if (looksBlocked(text)) {
        errors.push({ url, message: 'blocked or CAPTCHA page; not solved — use paste-URL' });
        continue;
      }
      const parsed = parseBoardPage(text, url);
      if (parsed.parsed) parsedOk += 1;
      linksFound += parsed.links.length;
      for (const link of parsed.links) {
        const rec = toStagingRecord(
          {
            ...link,
            sourceUrl: url,
            organization: source.name,
            extraText: source.opportunityType === 'apprenticeship' ? 'apprenticeship' : '',
            eligibility: ['Eligibility must be verified on the official site.'],
          },
          source,
          { collectedAt, collectorVersion: 'scrape-v1', listUrl: url }
        );
        rec.eligibilityParse = { complete: false };
        if (source.opportunityType) rec.opportunityType = source.opportunityType;
        records.push(rec);
      }
    } catch (err) {
      errors.push({ url: listUrl, message: err.message || String(err) });
    }
  }

  if (pages > 0 && parsedOk === 0) {
    errors.push({
      url: source.baseUrl || listUrls[0] || '',
      message: 'listUrls did not parse — use ops paste-URL',
    });
  }

  const unique = dedupeByUrl(records);
  return {
    records: unique,
    errors,
    metrics: { listUrls: listUrls.length, pages, parsedOk, links: linksFound, written: unique.length },
  };
}

module.exports = { collectGenericBoard, parseBoardPage, isBoardNoticeLink };
