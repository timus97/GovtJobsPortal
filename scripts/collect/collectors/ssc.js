const { fetchText, sleep } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { extractLinks } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { isPdfUrl } = require('../lib/pdfExtract');
const { withBrowser } = require('../lib/browser');
const {
  parseCalendarHtml,
  parseCalendarPdfUrl,
  findCalendarPdfLinks,
  looksBlocked,
  isFullIsoDate,
  officialUrlForRow,
} = require('../lib/calendarPdf');

const NOTICE_RE =
  /notif|apply|advert|exam|recruit|vacanc|calendar|corrigend|notice|advt|engagement|opening|circular|cgl|chsl|mts|cpo|steno/i;

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

function useBrowser(source) {
  return source.render === 'browser' || source.method === 'browser_scrape';
}

function guessSelectionProcess(name) {
  const blob = String(name || '');
  if (/written|tier|prelim|mains/i.test(blob) && !/cbt|computer\s*[- ]?based/i.test(blob)) {
    return 'written_multi_stage';
  }
  return 'cbt';
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
    (selectionProcess === 'cbt' ? 'computer based test SSC' : 'written examination multi stage SSC');
  const rec = toStagingRecord(
    {
      organization: 'Staff Selection Commission',
      orgType: source.orgTypeDefault || 'central',
      location: 'All India',
      hasExam: true,
      selectionProcess,
      extraText,
      eligibility: ['Eligibility must be verified on the official site.'],
      summary:
        extras.summary ||
        `${item.title || 'SSC examination'}. Eligibility must be verified on the official site.`,
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
      extraText: 'computer based test SSC',
      summary: bits.join(' '),
      examDate: row.examDate || null,
    }
  );
}

async function fetchPagesHtml(listUrls, source, ctx, errors, delayMs) {
  const pages = [];
  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl, { delayMs, retries: 1, timeoutMs: 20000 });
      const rawName = `list-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`;
      saveRaw(source.sourceId, ctx.runId, rawName, text);
      if (looksBlocked(text)) {
        errors.push({ url, message: 'blocked or CAPTCHA page; not solved' });
        continue;
      }
      pages.push({ url, html: text, listUrl });
    } catch (err) {
      const msg = err.message || String(err);
      errors.push({
        url: listUrl,
        message: /HTTP 403|HTTP 429|captcha|blocked/i.test(msg) ? `blocked: ${msg}` : msg,
      });
    }
  }
  return pages;
}

async function fetchPagesBrowser(listUrls, source, ctx, errors, delayMs) {
  const pages = [];
  await withBrowser(async ({ page }) => {
    for (const listUrl of listUrls) {
      await sleep(delayMs);
      try {
        await page.goto(listUrl, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(2500);
        try {
          await page.waitForLoadState('networkidle', { timeout: 12000 });
        } catch {
          /* JS-heavy boards often never idle */
        }
        const html = await page.content();
        const finalUrl = page.url();
        const rawName = `browser-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`;
        saveRaw(source.sourceId, ctx.runId, rawName, html);
        if (looksBlocked(html)) {
          errors.push({ url: finalUrl, message: 'blocked or CAPTCHA page; not solved' });
          continue;
        }
        pages.push({ url: finalUrl, html, listUrl });
      } catch (err) {
        errors.push({ url: listUrl, message: `playwright: ${err.message}` });
      }
    }
  });
  return pages;
}

async function collectSsc(source, ctx) {
  const { collectedAt, pdfCounter } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source);
  const delayMs = Number(source.rateLimitMs || 800);
  const records = [];
  let pages = [];
  let method = 'none';
  let calendarRows = 0;
  let linksFound = 0;

  if (useBrowser(source)) {
    try {
      pages = await fetchPagesBrowser(listUrls, source, ctx, errors, delayMs);
      method = 'playwright';
    } catch (err) {
      errors.push({ url: 'browser', message: err.message });
    }
  }

  if (pages.length === 0) {
    method = useBrowser(source) ? 'http-fallback' : 'html';
    pages = await fetchPagesHtml(listUrls, source, ctx, errors, delayMs);
  }

  for (const page of pages) {
    const parsed = parseCalendarHtml(page.html, page.url);
    calendarRows += parsed.length;
    for (const row of parsed) {
      records.push(calendarToRecord(row, source, page.url, collectedAt, 'calendar-v1'));
    }

    const boardLinks = extractBoardLinks(page.html, page.url);
    linksFound += boardLinks.length;
    for (const link of boardLinks) {
      records.push(
        toExamRecord(
          { ...link, sourceUrl: page.url },
          source,
          {
            collectedAt,
            collectorVersion: method === 'playwright' ? 'playwright-v1' : 'scrape-v1',
            listUrl: page.url,
          },
          {
            extraText: 'computer based test SSC',
            summary: `${link.title}. Eligibility must be verified on the official site.`,
          }
        )
      );
    }

    const pdfLinks = findCalendarPdfLinks(page.html, page.url).filter((l) => isPdfUrl(l.href));
    for (const pdfLink of pdfLinks.slice(0, 2)) {
      await sleep(delayMs);
      const parsedPdf = await parseCalendarPdfUrl(pdfLink.href, pdfCounter, { pageUrl: page.url });
      if (!parsedPdf.ok) {
        errors.push({ url: pdfLink.href, message: parsedPdf.error || 'calendar PDF parse failed' });
        continue;
      }
      calendarRows += parsedPdf.rows.length;
      for (const row of parsedPdf.rows) {
        if (!row.officialUrl) row.officialUrl = pdfLink.href;
        records.push(calendarToRecord(row, source, page.url, collectedAt, 'pdf-v1'));
      }
    }
  }

  const unique = dedupeByUrl(records);
  return {
    records: unique,
    errors,
    metrics: {
      method,
      listUrls: listUrls.length,
      pages: pages.length,
      calendarRows,
      links: linksFound,
      written: unique.length,
    },
  };
}

module.exports = { collectSsc };
