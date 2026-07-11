const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { extractPdf, isPdfUrl } = require('../lib/pdfExtract');
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
  const listUrls = candidateUrls(source);
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
        const found = extractLinks(text, url, { limit: 50 }).map((l) => ({ ...l, sourceUrl: url }));
        links.push(...found);
        // Stop probing extra career paths once we have job-like links
        if (found.length >= 3) break;
      } catch (err) {
        errors.push({ url: listUrl, message: err.message });
      }
    }
  }

  const records = [];
  for (const item of links) {
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
        collectorVersion: source.render === 'browser' ? 'playwright-v1' : 'scrape-v1',
        listUrl: item.sourceUrl,
      })
    );
  }

  return {
    records: dedupeByUrl(records),
    errors,
    metrics: { links: links.length, written: dedupeByUrl(records).length },
  };
}

module.exports = { collectGenericCareers };
