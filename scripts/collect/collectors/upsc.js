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
  /notif|apply|advert|exam|recruit|vacanc|calendar|corrigend|notice|advt|engagement|opening|circular|active/i;

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

function guessSelectionProcess(name) {
  const blob = String(name || '');
  if (/computer\s*[- ]?based|cbt/i.test(blob)) return 'cbt';
  return 'written_multi_stage';
}

function extractBoardLinks(html, pageUrl) {
  return extractLinks(html, pageUrl, { jobLikeOnly: false, limit: 80 }).filter((l) => {
    const blob = `${l.title} ${l.href}`;
    return NOTICE_RE.test(blob);
  });
}

function toExamRecord(item, source, meta, extras = {}) {
  const selectionProcess = extras.selectionProcess || guessSelectionProcess(item.title);
  const extraText =
    extras.extraText ||
    (selectionProcess === 'cbt' ? 'computer based test' : 'written examination multi stage');
  const rec = toStagingRecord(
    {
      organization: 'Union Public Service Commission',
      orgType: source.orgTypeDefault || 'central',
      location: 'All India',
      hasExam: true,
      selectionProcess,
      extraText,
      eligibility: ['Eligibility must be verified on the official site.'],
      summary:
        extras.summary ||
        `${item.title || 'UPSC examination'}. Eligibility must be verified on the official site.`,
      ...item,
      hasExam: true,
      selectionProcess,
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
      selectionProcess: guessSelectionProcess(row.name),
      extraText: 'written examination multi stage UPSC',
      summary: bits.join(' '),
      examDate: row.examDate || null,
    }
  );
}

async function collectUpsc(source, ctx) {
  const { runId, collectedAt, pdfCounter } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source);
  const delayMs = Number(source.rateLimitMs || 600);
  const records = [];
  let pages = 0;
  let calendarRows = 0;
  let linksFound = 0;

  for (const listUrl of listUrls) {
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

      const boardLinks = extractBoardLinks(text, url);
      linksFound += boardLinks.length;
      for (const link of boardLinks) {
        records.push(
          toExamRecord(
            { ...link, sourceUrl: url },
            source,
            { collectedAt, collectorVersion: 'scrape-v1', listUrl: url },
            {
              extraText: 'written examination multi stage UPSC',
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
          if (!row.officialUrl) row.officialUrl = pdfLink.href;
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

module.exports = { collectUpsc };
