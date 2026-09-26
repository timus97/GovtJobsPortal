/**
 * Turn scored notice links into staging records.
 * PDF-only pages: download, parse, keep only recruitment notices.
 */
const { toStagingRecord, dedupeByUrl } = require('./toStaging');
const { extractPdf, isPdfUrl } = require('./pdfExtract');
const { contentTypeOf, isPdfContentType } = require('./http');
const { filterJobLinks, looksLikeDocumentHref } = require('./jobLinkQuality');

async function hrefLooksLikePdf(href) {
  if (isPdfUrl(href) || looksLikeDocumentHref(href)) return true;
  const probed = await contentTypeOf(href);
  return Boolean(probed.contentType && isPdfContentType(probed.contentType) && /pdf/i.test(probed.contentType));
}

async function recordFromNoticeLink(item, source, ctx, extras = {}) {
  const { collectedAt, pdfCounter } = ctx;
  const href = item.href || item.officialUrl;
  let usedPdf = false;
  let payload = { ...item, href };

  const tryPdf = item.isPdf || isPdfUrl(href) || looksLikeDocumentHref(href);
  if (tryPdf) {
    if (!pdfCounter) return { skip: true, reason: 'pdf_no_counter', url: href };
    const pdf = await extractPdf(href, pdfCounter);
    if (!pdf.ok) {
      return { skip: true, reason: pdf.skipped ? 'pdf_skipped' : 'pdf_parse_failed', url: href };
    }
    if (!pdf.isJobNotice) {
      return { skip: true, reason: 'pdf_not_recruitment', url: href };
    }
    usedPdf = true;
    payload = {
        ...item,
        href,
        officialUrl: href,
        title: item.title && item.title.length > 12 ? item.title : pdf.titleGuess || item.title,
        summary: pdf.excerpt,
        extraText: pdf.text?.slice(0, 4000),
        pdfText: pdf.text?.slice(0, 8000),
        lastDate: item.lastDate || pdf.lastDate,
        vacancies: item.vacancies ?? pdf.vacancies,
        pdfHash: pdf.hash,
        hasExam: pdf.classified?.hasExam === true ? true : item.hasExam,
        selectionProcess: pdf.classified?.selectionProcess || item.selectionProcess,
      };
  }

  const rec = toStagingRecord(
    {
      ...extras,
      ...payload,
      organization: extras.organization || payload.organization,
    },
    source,
    {
      collectedAt,
      collectorVersion: usedPdf ? 'pdf-v1' : extras.collectorVersion || 'scrape-v1',
      listUrl: item.sourceUrl,
    }
  );
  if (extras.eligibilityParse) rec.eligibilityParse = extras.eligibilityParse;
  if (extras.opportunityType) rec.opportunityType = extras.opportunityType;
  return { record: rec };
}

async function recordsFromNoticeLinks(links, source, ctx, extras = {}) {
  const errors = [];
  const records = [];
  const kept = filterJobLinks(links, source);
  for (const item of kept) {
    try {
      const out = await recordFromNoticeLink(item, source, ctx, extras);
      if (out.skip) {
        errors.push({ url: out.url, message: out.reason });
        try {
          require('./collectProgress').noteFinding({
            sourceId: source.sourceId,
            sourceName: source.name,
            pageUrl: item.sourceUrl || source.baseUrl || '',
            url: out.url || item.href,
            kind: item.isPdf || /\.pdf(\?|#|$)/i.test(out.url || item.href || '') ? 'pdf' : 'html',
            status: 'skipped',
            reason: out.reason,
            title: item.title || item.text || '',
          });
        } catch {
          /* progress is optional */
        }
        continue;
      }
      if (out.record) records.push(out.record);
    } catch (err) {
      errors.push({ url: item.href, message: err.message || String(err) });
    }
  }
  return { records: dedupeByUrl(records), errors, considered: links.length, kept: kept.length };
}

module.exports = { recordsFromNoticeLinks, recordFromNoticeLink, hrefLooksLikePdf };
