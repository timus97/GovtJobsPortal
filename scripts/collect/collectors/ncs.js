const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { recordsFromNoticeLinks } = require('../lib/collectNotices');
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

  if (links.length === 0) {
    errors.push({
      url: listUrls[0] || source.baseUrl || '',
      message: 'Playwright returned no keepable NCS notices; HTTP homepage scrape skipped',
    });
    return {
      records: [],
      errors,
      metrics: { method: used === 'playwright' ? 'playwright-empty' : 'playwright-failed', links: 0, kept: 0, written: 0 },
    };
  }

  const built = await recordsFromNoticeLinks(
    links,
    source,
    { collectedAt, pdfCounter: ctx.pdfCounter },
    {
      organization: 'Government of India (via NCS)',
      orgType: 'central',
      collectorVersion: used === 'playwright' ? 'playwright-v1' : 'scrape-v1',
    }
  );
  errors.push(...built.errors);

  return {
    records: built.records,
    errors,
    metrics: { method: used, links: links.length, kept: built.kept, written: built.records.length },
  };
}

module.exports = { collectNcs };
