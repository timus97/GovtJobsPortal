/**
 * Parse official exam-calendar HTML tables / PDF text into metadata rows.
 * Never copies PDF bytes to client/public — text only.
 */
const cheerio = require('cheerio');
const { parseDateFromText } = require('./dates');
const { extractLinks, absoluteUrl } = require('./htmlLinks');
const { extractPdf, isPdfUrl } = require('./pdfExtract');

const MONTHS = {
  jan: 0,
  january: 0,
  feb: 1,
  february: 1,
  mar: 2,
  march: 2,
  apr: 3,
  april: 3,
  may: 4,
  jun: 5,
  june: 5,
  jul: 6,
  july: 6,
  aug: 7,
  august: 7,
  sep: 8,
  sept: 8,
  september: 8,
  oct: 9,
  october: 9,
  nov: 10,
  november: 10,
  dec: 11,
  december: 11,
};

const SKIP_NAME_RE =
  /^(name of (the )?exam(?:ination)?|examination name|s\.?\s*no\.?|sl\.?\s*no\.?|date of notification|last date|remarks|total|tentative calendar)$/i;

const EMPTY_DATE_RE = /^(to be notified|yet to be notified|tbn|tbd|n\/?a|nil|-+|–+|—+)$/i;

const DATE_TOKEN_RE =
  /\b(\d{1,2}(?:st|nd|rd|th)?[.\-\/]\d{1,2}[.\-\/]20\d{2}|\d{1,2}(?:st|nd|rd|th)?[\s,]+\w{3,9}[\s,]+20\d{2}|\w{3,9}\s+\d{1,2}(?:st|nd|rd|th)?[,\s]+20\d{2}|\w{3,9}\.?,?\s*[-–/to]+\s*\w{3,9}\.?,?\s*20\d{2}|\w{3,9}\.?,?\s+20\d{2})\b/gi;

function isFullIsoDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(String(value || ''));
}

function monthIndex(token) {
  if (!token) return null;
  const key = String(token).toLowerCase().replace(/\./g, '');
  if (MONTHS[key] != null) return MONTHS[key];
  const short = key.slice(0, 3);
  return MONTHS[short] != null ? MONTHS[short] : null;
}

function yyyymm(year, monthIdx) {
  if (monthIdx == null || !year) return null;
  return `${year}-${String(monthIdx + 1).padStart(2, '0')}`;
}

function parseLooseDate(text) {
  if (text == null) return null;
  const s = String(text).replace(/\s+/g, ' ').trim();
  if (!s || EMPTY_DATE_RE.test(s)) return null;

  const iso = parseDateFromText(s);
  if (iso) return iso;

  const range = s.match(
    /\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\.?\s*[-–/to]+\s*(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\.?,?\s*(20\d{2})\b/i
  );
  if (range) {
    const ym = yyyymm(range[2], monthIndex(range[1]));
    if (ym) return ym;
  }

  const my = s.match(
    /\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\.?,?\s*(20\d{2})\b/i
  );
  if (my) {
    const ym = yyyymm(my[2], monthIndex(my[1]));
    if (ym) return ym;
  }

  return null;
}

function extractDateTokens(text) {
  const s = String(text || '');
  const out = [];
  let m;
  const re = new RegExp(DATE_TOKEN_RE.source, 'gi');
  while ((m = re.exec(s))) {
    out.push(m[1] || m[0]);
  }
  return out;
}

function looksBlocked(html) {
  const s = String(html || '').slice(0, 24000);
  return /recaptcha|g-recaptcha|h-captcha|hcaptcha|id=["']captcha|class=["'][^"']*captcha|access denied|unusual traffic|please verify you are (a )?human|request unsuccessful|cf-browser-verification|pardon our interruption/i.test(
    s
  );
}

function classifyHeader(text) {
  const t = String(text || '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
  if (!t) return null;
  if (/\b(link|url|website|apply here|notification pdf|click here)\b/.test(t) && !/date/.test(t)) {
    return 'officialUrl';
  }
  if (/notif|date of advt|advertisement date/.test(t)) return 'notificationDate';
  if (
    /last\s*date|closing date|receipt of appl|completion of appl|closing of appl|apply\s*(by|upto|until|on or before)/.test(
      t
    )
  ) {
    return 'lastDate';
  }
  if (/(exam|commencement|tier).*(date|month)|date.*exam|month of exam|tentative month/.test(t)) {
    return 'examDate';
  }
  if (/^name\b|name of|examination name|exam name|name of the exam|post|vacancy title/.test(t)) {
    return 'name';
  }
  if (/^examination$|^exam$/.test(t)) return 'name';
  return null;
}

function cellText($, el) {
  return $(el)
    .text()
    .replace(/\s+/g, ' ')
    .trim();
}

function cellHref($, el, pageUrl) {
  const raw =
    $(el).find('a[href]').first().attr('href') ||
    ($(el).is('a') ? $(el).attr('href') : null);
  return absoluteUrl(pageUrl, raw);
}

function normalizeCalendarRow(row, defaults = {}) {
  const name = String(row?.name || '')
    .replace(/\s+/g, ' ')
    .replace(/^[\d.)\s-]+/, '')
    .trim();
  if (!name || name.length < 6) return null;
  if (SKIP_NAME_RE.test(name)) return null;
  if (/^https?:\/\//i.test(name) && name.length < 16) return null;

  const officialUrl =
    (row.officialUrl && /^https?:\/\//i.test(row.officialUrl) && row.officialUrl) ||
    defaults.officialUrl ||
    defaults.pageUrl ||
    null;

  return {
    name: name.slice(0, 240),
    notificationDate: parseLooseDate(row.notificationDate) || null,
    lastDate: parseLooseDate(row.lastDate) || null,
    examDate: parseLooseDate(row.examDate) || null,
    officialUrl,
  };
}

function fillDatesInOrder(partial, tokens) {
  const slots = ['notificationDate', 'lastDate', 'examDate'];
  let i = 0;
  for (const slot of slots) {
    if (partial[slot]) continue;
    while (i < tokens.length && !parseLooseDate(tokens[i])) i += 1;
    if (i < tokens.length) {
      partial[slot] = tokens[i];
      i += 1;
    }
  }
}

function parseTableRow($, cells, headers, pageUrl) {
  const partial = {
    name: null,
    notificationDate: null,
    lastDate: null,
    examDate: null,
    officialUrl: null,
  };
  const leftoverDates = [];
  const leftoverText = [];

  cells.forEach((el, idx) => {
    const text = cellText($, el);
    const href = cellHref($, el, pageUrl);
    const role = headers[idx] || null;

    if (href && (!partial.officialUrl || /notif|apply|advert|\.pdf/i.test(href))) {
      partial.officialUrl = href;
    }

    if (role === 'officialUrl' && href) {
      partial.officialUrl = href;
      return;
    }
    if (role === 'name') {
      partial.name = text;
      return;
    }
    if (role === 'notificationDate' || role === 'lastDate' || role === 'examDate') {
      partial[role] = text;
      return;
    }

    if (text) leftoverText.push(text);
    leftoverDates.push(...extractDateTokens(text));
  });

  if (!partial.name) {
    const numbered = leftoverText[0] && /^\d{1,3}\.?$/.test(leftoverText[0]);
    const candidate = leftoverText.find((t, i) => {
      if (numbered && i === 0) return false;
      if (parseLooseDate(t) && t.length < 24) return false;
      return t.length >= 6 && !SKIP_NAME_RE.test(t);
    });
    if (candidate) partial.name = candidate;
  }

  fillDatesInOrder(partial, leftoverDates);
  return normalizeCalendarRow(partial, { pageUrl });
}

function parseCalendarHtml(html, pageUrl, options = {}) {
  const $ = cheerio.load(html || '');
  const rows = [];
  const seen = new Set();

  $('table').each((_, table) => {
    const $table = $(table);
    let headers = [];
    const headerRow = $table.find('thead tr').first().length
      ? $table.find('thead tr').first()
      : $table.find('tr').first();
    if (headerRow.length) {
      headerRow.find('th, td').each((__, el) => {
        headers.push(classifyHeader(cellText($, el)));
      });
    }

    const mapped = headers.filter(Boolean).length;
    const $rows = $table.find('tr');

    $rows.each((idx, tr) => {
      const $tr = $(tr);
      const cellTexts = $tr
        .find('th, td')
        .toArray()
        .map((el) => cellText($, el));
      const headerish = cellTexts.filter((t) => classifyHeader(t)).length >= 2;
      if (headerish && (idx === 0 || mapped >= 2)) return;
      if ($tr.find('th').length && !$tr.find('td').length) return;
      const cells = $tr.find('th, td').toArray();
      if (cells.length < 2) return;
      const row = parseTableRow($, cells, headers, pageUrl || options.pageUrl);
      if (!row) return;
      const key = `${row.name}|${row.notificationDate || ''}|${row.examDate || ''}`;
      if (seen.has(key)) return;
      seen.add(key);
      rows.push(row);
    });
  });

  return rows;
}

function isHeaderLine(line) {
  return /name of exam|date of notification|last date|date of commencement|tentative date|tentative month/i.test(
    line
  );
}

function isJunkLine(line) {
  return /^(page\s+\d+|www\.|https?:\/\/|union public service|staff selection commission|annual calendar|tentative calendar|note:|remarks[:\s])/i.test(
    line
  );
}

function parseCalendarText(text, options = {}) {
  const pageUrl = options.pageUrl || options.officialUrl || null;
  const lines = String(text || '')
    .replace(/\r/g, '')
    .split('\n')
    .map((l) => l.replace(/\s+/g, ' ').trim())
    .filter(Boolean);

  const rows = [];
  const seen = new Set();
  let pendingName = '';

  for (const line of lines) {
    if (isHeaderLine(line) || isJunkLine(line)) {
      pendingName = '';
      continue;
    }

    const tokens = extractDateTokens(line);
    if (tokens.length === 0) {
      if (line.length >= 6 && !SKIP_NAME_RE.test(line)) pendingName = line;
      continue;
    }

    const nameFromLine = line
      .replace(new RegExp(DATE_TOKEN_RE.source, 'gi'), ' ')
      .replace(/[|]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    const name = nameFromLine || pendingName;
    pendingName = '';

    const partial = { name, officialUrl: pageUrl };
    fillDatesInOrder(partial, tokens);
    const row = normalizeCalendarRow(partial, { pageUrl });
    if (!row) continue;
    const key = `${row.name}|${row.notificationDate || ''}|${row.examDate || ''}`;
    if (seen.has(key)) continue;
    seen.add(key);
    rows.push(row);
  }

  return rows;
}

async function parseCalendarPdfBuffer(buffer, options = {}) {
  let pdfParse;
  try {
    pdfParse = require('pdf-parse');
  } catch {
    return { ok: false, rows: [], error: 'pdf-parse not installed' };
  }
  if (!buffer || !buffer.length) {
    return { ok: false, rows: [], error: 'empty PDF buffer' };
  }
  try {
    const parsed = await pdfParse(buffer);
    const text = parsed && parsed.text ? parsed.text : '';
    return { ok: true, rows: parseCalendarText(text, options), text };
  } catch (err) {
    return { ok: false, rows: [], error: `PDF parse failed: ${err.message}` };
  }
}

function findCalendarPdfLinks(html, pageUrl) {
  const links = extractLinks(html, pageUrl, { jobLikeOnly: false, limit: 80 });
  const pageIsCalendar = /calendar|programme|schedule/i.test(pageUrl || '');
  return links
    .filter((l) => {
      if (!l.isPdf && !isPdfUrl(l.href)) return false;
      const blob = `${l.title} ${l.href}`;
      if (pageIsCalendar) return true;
      return /calendar|programme|schedule|time[\s-]?table/i.test(blob);
    })
    .slice(0, 5);
}

/**
 * Download + extract a calendar PDF via pdfExtract (raw cache only, not public).
 */
async function parseCalendarPdfUrl(url, pdfCounter, options = {}) {
  try {
    const pdf = await extractPdf(url, pdfCounter);
    if (!pdf.ok) return { ok: false, rows: [], error: pdf.error || 'PDF extract failed', url };
    return {
      ok: true,
      rows: parseCalendarText(pdf.text || '', { ...options, officialUrl: url, pageUrl: url }),
      hash: pdf.hash,
      url,
    };
  } catch (err) {
    return { ok: false, rows: [], error: err.message, url };
  }
}

function officialUrlForRow(row, listUrl) {
  if (row?.officialUrl && /^https?:\/\//i.test(row.officialUrl)) return row.officialUrl;
  const slug = String(row?.name || 'exam')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80);
  return `${listUrl}#${slug || 'exam'}`;
}

module.exports = {
  parseCalendarHtml,
  parseCalendarText,
  parseCalendarPdfBuffer,
  parseCalendarPdfUrl,
  findCalendarPdfLinks,
  normalizeCalendarRow,
  looksBlocked,
  isFullIsoDate,
  officialUrlForRow,
  parseLooseDate,
};
