const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { recordsFromNoticeLinks } = require('../lib/collectNotices');

async function collectBecil(source, ctx) {
  const { runId } = ctx;
  const errors = [];
  const listUrls = source.listUrls?.length ? source.listUrls : [source.baseUrl].filter(Boolean);
  const items = [];

  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl);
      saveRaw(source.sourceId, runId, 'list.html', text);
      const links = extractLinks(text, url, { limit: 50 });
      for (const link of links) items.push({ ...link, sourceUrl: url });
    } catch (err) {
      errors.push({ url: listUrl, message: err.message });
    }
  }

  const built = await recordsFromNoticeLinks(items, source, ctx, { collectorVersion: 'scrape-v1' });
  errors.push(...built.errors);
  return {
    records: built.records,
    errors,
    metrics: { listUrls: listUrls.length, links: items.length, kept: built.kept, written: built.records.length },
  };
}

module.exports = { collectBecil };
