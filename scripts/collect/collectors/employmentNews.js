const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { extractPdf, isPdfUrl } = require('../lib/pdfExtract');

async function collectEmploymentNews(source, ctx) {
  const { runId, collectedAt, pdfCounter } = ctx;
  const errors = [];
  const listUrls = source.listUrls?.length ? source.listUrls : [source.baseUrl].filter(Boolean);
  const items = [];

  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl);
      saveRaw(source.sourceId, runId, 'index.html', text);
      const links = extractLinks(text, url, { limit: 60 });
      items.push(...links.map((l) => ({ ...l, sourceUrl: url })));
    } catch (err) {
      errors.push({ url: listUrl, message: err.message });
    }
  }

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
                title: item.title || pdf.titleGuess || 'Employment News vacancy',
                summary: pdf.excerpt,
                extraText: pdf.text?.slice(0, 4000),
                pdfText: pdf.text?.slice(0, 10000),
                lastDate: pdf.lastDate,
                pdfHash: pdf.hash,
                hasExam: pdf.classified?.hasExam === true ? true : undefined,
                selectionProcess: pdf.classified?.selectionProcess || undefined,
                organization: 'As per Employment News notification',
              },
              source,
              { collectedAt, collectorVersion: 'pdf-v1', listUrl: item.sourceUrl }
            )
          );
          continue;
        }
        if (!pdf.skipped) errors.push({ url: item.href, message: pdf.error });
      } catch (err) {
        errors.push({ url: item.href, message: err.message });
      }
    }
    records.push(
      toStagingRecord(
        {
          ...item,
          organization: item.organization || 'As per Employment News notification',
        },
        source,
        { collectedAt, collectorVersion: 'scrape-v1', listUrl: item.sourceUrl }
      )
    );
  }

  return {
    records: dedupeByUrl(records),
    errors,
    metrics: { links: items.length, written: dedupeByUrl(records).length },
  };
}

module.exports = { collectEmploymentNews };
