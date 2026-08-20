const { fetchText, sleep } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { withBrowser } = require('../lib/browser');
const { looksBlocked } = require('../lib/calendarPdf');

const NOTICE_RE =
  /cen|apply|recruit|notif|vacanc|group\s*d|ntpc|alp|technician|junior engineer|\bje\b|advert|opening|employment notice/i;
const SKIP_RE = /login|scribe|faq|twitter|facebook|youtube|instagram/i;

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

function isChandigarhZonalHost(url) {
  try {
    return /(^|\.)rrbcdg\.gov\.in$/i.test(new URL(url).hostname);
  } catch {
    return /rrbcdg\.gov\.in/i.test(String(url || ''));
  }
}

function isNationalApplyHost(url) {
  try {
    return /(^|\.)rrbapply\.gov\.in$/i.test(new URL(url).hostname);
  } catch {
    return /rrbapply\.gov\.in/i.test(String(url || ''));
  }
}

function useBrowser(source) {
  return source.render === 'browser' || source.method === 'browser_scrape';
}

function isRrbNoticeLink(link) {
  const blob = `${link.title || ''} ${link.href || ''}`;
  if (SKIP_RE.test(blob)) return false;
  return NOTICE_RE.test(blob);
}

function toExamRecord(item, source, meta) {
  const rec = toStagingRecord(
    {
      organization: source.name || 'Railway Recruitment Board',
      orgType: source.orgTypeDefault || 'central',
      location: /chennai/i.test(source.sourceId || source.name || '') ? 'Chennai' : 'All India',
      hasExam: true,
      selectionProcess: 'cbt',
      extraText: 'computer based test RRB CEN',
      eligibility: ['Eligibility must be verified on the official site.'],
      summary:
        `${item.title || 'RRB recruitment'}. Eligibility must be verified on the official site.`,
      ...item,
      hasExam: true,
      selectionProcess: 'cbt',
    },
    source,
    meta
  );
  rec.eligibilityParse = { complete: false };
  return rec;
}

async function fetchPagesHtml(listUrls, source, ctx, errors, delayMs) {
  const pages = [];
  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl, { delayMs, retries: 1, timeoutMs: 20000 });
      const rawName = `list-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`;
      saveRaw(source.sourceId, ctx.runId, rawName, text);
      if (looksBlocked(text)) {
        errors.push({ url, message: 'blocked or CAPTCHA page; not solved' });
        continue;
      }
      pages.push({ url, html: text, listUrl });
    } catch (err) {
      const msg = err.message || String(err);
      errors.push({
        url: listUrl,
        message: /HTTP 403|HTTP 429|captcha|blocked/i.test(msg) ? `blocked: ${msg}` : msg,
      });
    }
  }
  return pages;
}

async function fetchPagesBrowser(listUrls, source, ctx, errors, delayMs) {
  const pages = [];
  await withBrowser(async ({ page }) => {
    for (const listUrl of listUrls) {
      await sleep(delayMs);
      try {
        await page.goto(listUrl, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(2500);
        try {
          await page.waitForLoadState('networkidle', { timeout: 12000 });
        } catch {
          /* apply portals often stay busy */
        }
        const html = await page.content();
        const finalUrl = page.url();
        const rawName = `browser-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`;
        saveRaw(source.sourceId, ctx.runId, rawName, html);
        if (looksBlocked(html)) {
          errors.push({ url: finalUrl, message: 'blocked or CAPTCHA page; not solved' });
          continue;
        }
        pages.push({ url: finalUrl, html, listUrl });
      } catch (err) {
        errors.push({ url: listUrl, message: `playwright: ${err.message}` });
      }
    }
  });
  return pages;
}

async function collectRrb(source, ctx) {
  const { collectedAt } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source);
  const delayMs = Number(source.rateLimitMs || 800);
  const records = [];
  let pages = [];
  let method = 'none';
  let linksFound = 0;

  const allowed = [];
  for (const url of listUrls) {
    if (isChandigarhZonalHost(url) && isNationalApplyHost(source.baseUrl || '')) {
      errors.push({
        url,
        message: 'rrbcdg.gov.in is Chandigarh zonal, not the national RRB apply host',
      });
      continue;
    }
    if (isChandigarhZonalHost(url) && source.sourceId === 'rrb_apply') {
      errors.push({
        url,
        message: 'rrbcdg.gov.in is Chandigarh zonal, not the national RRB apply host',
      });
      continue;
    }
    allowed.push(url);
  }

  if (useBrowser(source)) {
    try {
      pages = await fetchPagesBrowser(allowed, source, ctx, errors, delayMs);
      method = 'playwright';
    } catch (err) {
      errors.push({ url: 'browser', message: err.message });
    }
  }

  if (pages.length === 0) {
    method = useBrowser(source) ? 'http-fallback' : 'html';
    pages = await fetchPagesHtml(allowed, source, ctx, errors, delayMs);
  }

  for (const page of pages) {
    const links = extractLinks(page.html, page.url, { jobLikeOnly: false, limit: 80 }).filter(isRrbNoticeLink);
    linksFound += links.length;
    for (const link of links) {
      records.push(
        toExamRecord(
          { ...link, sourceUrl: page.url },
          source,
          {
            collectedAt,
            collectorVersion: method === 'playwright' ? 'playwright-v1' : 'scrape-v1',
            listUrl: page.url,
          }
        )
      );
    }
  }

  const unique = dedupeByUrl(records);
  return {
    records: unique,
    errors,
    metrics: {
      method,
      listUrls: allowed.length,
      pages: pages.length,
      links: linksFound,
      written: unique.length,
    },
  };
}

module.exports = {
  collectRrb,
  isChandigarhZonalHost,
  isNationalApplyHost,
  isRrbNoticeLink,
};
