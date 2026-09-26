const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { recordsFromNoticeLinks } = require('../lib/collectNotices');
const { filterJobLinks } = require('../lib/jobLinkQuality');
const { withBrowser, extractJobLinksFromPage } = require('../lib/browser');

function candidateUrls(source) {
  const urls = [];
  if (source.listUrls?.length) urls.push(...source.listUrls);
  if (source.baseUrl) urls.push(source.baseUrl);
  // de-dupe preserve order
  const seen = new Set();
  return urls.filter((u) => {
    if (!u || seen.has(u)) return false;
    seen.add(u);
    return true;
  });
}

async function collectGenericCareers(source, ctx) {
  const { runId, collectedAt, pdfCounter } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source).sort((a, b) => {
    const score = (u) => (/career|recruit|vacanc/i.test(u) ? 0 : 1);
    return score(a) - score(b);
  });
  let links = [];
  const maxUrls = Number(process.env.COLLECT_MAX_URLS_PER_SOURCE || 5);

  if (source.render === 'browser') {
    try {
      links = await withBrowser(async ({ page }) => {
        const all = [];
        for (const listUrl of listUrls.slice(0, maxUrls)) {
          try {
            const { links: pageLinks, html, finalUrl } = await extractJobLinksFromPage(page, listUrl);
            saveRaw(
              source.sourceId,
              runId,
              `browser-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`,
              html
            );
            all.push(...pageLinks.map((l) => ({ ...l, sourceUrl: finalUrl })));
            if (all.length >= 15) break;
          } catch (err) {
            errors.push({ url: listUrl, message: err.message });
          }
        }
        return all;
      });
    } catch (err) {
      errors.push({ url: 'browser', message: err.message });
    }
  }

  if (links.length === 0) {
    for (const listUrl of listUrls.slice(0, maxUrls)) {
      try {
        const { text, url } = await fetchText(listUrl, { delayMs: 600, retries: 1, timeoutMs: 15000 });
        saveRaw(
          source.sourceId,
          runId,
          `list-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`,
          text
        );
        const found = extractLinks(text, url, { limit: 80 }).map((l) => ({ ...l, sourceUrl: url }));
        const kept = filterJobLinks(found, source);
        links.push(...kept);
        if (kept.length >= 3) break;
      } catch (err) {
        errors.push({ url: listUrl, message: err.message });
      }
    }
  }

  const spaHost = /ncs\.gov|careers\.bhel|careers\.ntpc|betacloud|job-listing/i.test(
    `${source.baseUrl || ''} ${(source.listUrls || []).join(' ')}`
  );
  if (links.length === 0 && source.render !== 'browser' && spaHost) {
    try {
      links = await withBrowser(async ({ page }) => {
        const all = [];
        for (const listUrl of listUrls.slice(0, 2)) {
          try {
            const { links: pageLinks, html, finalUrl } = await extractJobLinksFromPage(page, listUrl);
            saveRaw(
              source.sourceId,
              runId,
              `spa-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`,
              html
            );
            all.push(...pageLinks.map((l) => ({ ...l, sourceUrl: finalUrl })));
          } catch (err) {
            errors.push({ url: listUrl, message: `playwright: ${err.message}` });
          }
        }
        return all;
      });
    } catch (err) {
      errors.push({ url: 'browser', message: err.message });
    }
  }

  const built = await recordsFromNoticeLinks(links, source, { collectedAt, pdfCounter }, {
    collectorVersion: source.render === 'browser' ? 'playwright-v1' : 'scrape-v1',
  });
  errors.push(...built.errors);

  return {
    records: built.records,
    errors,
    metrics: { links: links.length, kept: built.kept, written: built.records.length },
  };
}

module.exports = { collectGenericCareers };
