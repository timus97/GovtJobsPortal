const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { withBrowser, extractJobLinksFromPage } = require('../lib/browser');

async function collectNcs(source, ctx) {
  const { runId, collectedAt } = ctx;
  const errors = [];
  const listUrls = source.listUrls?.length ? source.listUrls : [source.baseUrl].filter(Boolean);
  let links = [];
  let used = 'none';

  // Playwright first
  try {
    const result = await withBrowser(async ({ page }) => {
      const all = [];
      for (const listUrl of listUrls) {
        try {
          const { links: pageLinks, html, finalUrl } = await extractJobLinksFromPage(page, listUrl);
          saveRaw(source.sourceId, runId, `playwright-${Buffer.from(listUrl).toString('base64url').slice(0, 40)}.html`, html);
          all.push(...pageLinks.map((l) => ({ ...l, sourceUrl: finalUrl })));
        } catch (err) {
          errors.push({ url: listUrl, message: `playwright: ${err.message}` });
        }
      }
      return all;
    });
    links = result;
    used = 'playwright';
  } catch (err) {
    errors.push({ url: 'browser', message: err.message });
  }

  // HTTP fallback
  if (links.length === 0) {
    used = 'http-fallback';
    for (const listUrl of listUrls) {
      try {
        const { text, url } = await fetchText(listUrl);
        saveRaw(source.sourceId, runId, 'http-fallback.html', text);
        links.push(...extractLinks(text, url, { limit: 60 }).map((l) => ({ ...l, sourceUrl: url })));
      } catch (err) {
        errors.push({ url: listUrl, message: err.message });
      }
    }
  }

  const records = dedupeByUrl(
    links.map((item) =>
      toStagingRecord(
        {
          ...item,
          organization: item.organization || 'Government of India (via NCS)',
          orgType: 'central',
        },
        source,
        {
          collectedAt,
          collectorVersion: used === 'playwright' ? 'playwright-v1' : 'scrape-v1',
          listUrl: item.sourceUrl,
        }
      )
    )
  );

  return {
    records,
    errors,
    metrics: { method: used, links: links.length, written: records.length },
  };
}

module.exports = { collectNcs };
