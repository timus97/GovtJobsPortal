const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { extractPdf, isPdfUrl } = require('../lib/pdfExtract');

async function collectBecil(source, ctx) {
  const { runId, collectedAt, pdfCounter } = ctx;
  const errors = [];
  const listUrls = source.listUrls?.length ? source.listUrls : [source.baseUrl].filter(Boolean);
  const items = [];

  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl);
      saveRaw(source.sourceId, runId, 'list.html', text);
      const links = extractLinks(text, url, { limit: 50 });
      for (const link of links) {
        items.push({ ...link, sourceUrl: url });
      }
    } catch (err) {
      errors.push({ url: listUrl, message: err.message });
    }
  }

  // Enrich a few PDFs
  const records = [];
  for (const item of items) {
    if (isPdfUrl(item.href) && pdfCounter) {
      try {
        const pdf = await extractPdf(item.href, pdfCounter);
        if (pdf.ok) {
          records.push(
            toStagingRecord(
              {
                ...item,
                title: item.title || pdf.titleGuess,
                summary: pdf.excerpt,
                extraText: pdf.text?.slice(0, 4000),
                pdfText: pdf.text?.slice(0, 8000),
                lastDate: pdf.lastDate,
                pdfHash: pdf.hash,
                hasExam: pdf.classified?.hasExam === true ? true : undefined,
                selectionProcess: pdf.classified?.selectionProcess || undefined,
              },
              source,
              { collectedAt, collectorVersion: 'pdf-v1', listUrl: item.sourceUrl }
            )
          );
          continue;
        }
      } catch (err) {
        errors.push({ url: item.href, message: err.message });
      }
    }
    records.push(
      toStagingRecord(item, source, {
        collectedAt,
        collectorVersion: 'scrape-v1',
        listUrl: item.sourceUrl,
      })
    );
  }

  return {
    records: dedupeByUrl(records),
    errors,
    metrics: { listUrls: listUrls.length, links: items.length, written: dedupeByUrl(records).length },
  };
}

module.exports = { collectBecil };
