const { fetchText, sleep } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { isPdfUrl } = require('../lib/pdfExtract');
const {
  parseCalendarHtml,
  parseCalendarPdfUrl,
  findCalendarPdfLinks,
  looksBlocked,
  isFullIsoDate,
  officialUrlForRow,
} = require('../lib/calendarPdf');

const NOTICE_RE =
  /crp|po\/?mt|clerk|csa|rrb|specialist|calendar|notif|apply|recruit|vacanc|advert|exam|window notification/i;
const SKIP_RE = /tender|rfp|iso\s*9001|fraudulent|caution|trademark|career at ibps|gallery|tender/i;

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

function isYearStampedCalendarPdf(url) {
  return /ibps_calendar_\d{4}/i.test(String(url || ''));
}

function isIbpsNoticeLink(link) {
  const blob = `${link.title || ''} ${link.href || ''}`;
  if (SKIP_RE.test(blob)) return false;
  return NOTICE_RE.test(blob);
}

function guessSelectionProcess() {
  return 'cbt';
}

function toExamRecord(item, source, meta, extras = {}) {
  const rec = toStagingRecord(
    {
      organization: 'Institute of Banking Personnel Selection',
      orgType: source.orgTypeDefault || 'central',
      location: 'All India',
      hasExam: true,
      selectionProcess: extras.selectionProcess || guessSelectionProcess(),
      extraText: extras.extraText || 'computer based test IBPS CRP',
      eligibility: ['Eligibility must be verified on the official site.'],
      summary:
        extras.summary ||
        `${item.title || 'IBPS examination'}. Eligibility must be verified on the official site.`,
      ...item,
      hasExam: true,
      selectionProcess: extras.selectionProcess || guessSelectionProcess(),
    },
    source,
    meta
  );
  rec.eligibilityParse = { complete: false };
  if (extras.examDate) rec.examDate = extras.examDate;
  return rec;
}

function calendarToRecord(row, source, listUrl, collectedAt, collectorVersion) {
  const officialUrl = officialUrlForRow(row, listUrl);
  const bits = [
    row.name,
    row.notificationDate ? `Notification: ${row.notificationDate}` : null,
    row.lastDate ? `Last date: ${row.lastDate}` : null,
    row.examDate ? `Exam: ${row.examDate}` : null,
    'Eligibility must be verified on the official site.',
  ].filter(Boolean);
  return toExamRecord(
    {
      title: row.name,
      href: officialUrl,
      officialUrl,
      sourceUrl: listUrl,
      notificationDate: isFullIsoDate(row.notificationDate) ? row.notificationDate : null,
      lastDate: isFullIsoDate(row.lastDate) ? row.lastDate : null,
    },
    source,
    { collectedAt, collectorVersion, listUrl },
    {
      extraText: 'computer based test IBPS CRP',
      summary: bits.join(' '),
      examDate: row.examDate || null,
    }
  );
}

async function collectIbps(source, ctx) {
  const { runId, collectedAt, pdfCounter } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source);
  const delayMs = Number(source.rateLimitMs || 700);
  const records = [];
  let pages = 0;
  let calendarRows = 0;
  let linksFound = 0;

  for (const listUrl of listUrls) {
    if (isYearStampedCalendarPdf(listUrl)) {
      errors.push({
        url: listUrl,
        message: 'year-stamped calendar PDF is not a durable listUrl; use the CRP updates listing page',
      });
      continue;
    }
    try {
      const { text, url } = await fetchText(listUrl, { delayMs, retries: 1, timeoutMs: 20000 });
      const rawName = `list-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`;
      saveRaw(source.sourceId, runId, rawName, text);
      pages += 1;

      if (looksBlocked(text)) {
        errors.push({ url, message: 'blocked or CAPTCHA page; not solved' });
        continue;
      }

      const parsed = parseCalendarHtml(text, url);
      calendarRows += parsed.length;
      for (const row of parsed) {
        records.push(calendarToRecord(row, source, url, collectedAt, 'calendar-v1'));
      }

      const boardLinks = extractLinks(text, url, { jobLikeOnly: false, limit: 80 }).filter(isIbpsNoticeLink);
      linksFound += boardLinks.length;
      for (const link of boardLinks) {
        if (isYearStampedCalendarPdf(link.href)) continue;
        records.push(
          toExamRecord(
            { ...link, sourceUrl: url },
            source,
            { collectedAt, collectorVersion: 'scrape-v1', listUrl: url },
            {
              extraText: 'computer based test IBPS CRP',
              summary: `${link.title}. Eligibility must be verified on the official site.`,
            }
          )
        );
      }

      const pdfLinks = findCalendarPdfLinks(text, url).filter((l) => isPdfUrl(l.href));
      for (const pdfLink of pdfLinks.slice(0, 2)) {
        await sleep(delayMs);
        const parsedPdf = await parseCalendarPdfUrl(pdfLink.href, pdfCounter, { pageUrl: url });
        if (!parsedPdf.ok) {
          errors.push({ url: pdfLink.href, message: parsedPdf.error || 'calendar PDF parse failed' });
          continue;
        }
        calendarRows += parsedPdf.rows.length;
        for (const row of parsedPdf.rows) {
          if (!row.officialUrl) row.officialUrl = url;
          records.push(calendarToRecord(row, source, url, collectedAt, 'pdf-v1'));
        }
      }
    } catch (err) {
      const msg = err.message || String(err);
      errors.push({
        url: listUrl,
        message: /HTTP 403|HTTP 429|captcha|blocked/i.test(msg) ? `blocked: ${msg}` : msg,
      });
    }
  }

  const unique = dedupeByUrl(records);
  return {
    records: unique,
    errors,
    metrics: {
      listUrls: listUrls.length,
      pages,
      calendarRows,
      links: linksFound,
      written: unique.length,
    },
  };
}

module.exports = { collectIbps, isIbpsNoticeLink, isYearStampedCalendarPdf };
