/**
 * Employment News — free JOB HIGHLIGHTS table only.
 * Do not scrape the paid e-paper or download vacancy PDFs.
 */
const cheerio = require('cheerio');
const { fetchText } = require('../lib/http');
const { saveRaw } = require('../lib/rawStore');
const { absoluteUrl } = require('../lib/htmlLinks');
const { toStagingRecord, dedupeByUrl } = require('../lib/toStaging');
const { parseDateFromText } = require('../lib/dates');
const { officialUrlForRow } = require('../lib/calendarPdf');

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

function headerRoles(cells) {
  return cells.map((text) => {
    const t = String(text || '').toLowerCase();
    if (/organisation|organization/.test(t)) return 'organization';
    if (/^post\b|name of post|vacancy/.test(t)) return 'post';
    if (/method|appointment/.test(t)) return 'method';
    if (/last\s*date|closing/.test(t)) return 'lastDate';
    return null;
  });
}

function tableIsHighlights($table) {
  const headerText = $table.find('tr').first().text().replace(/\s+/g, ' ');
  return /organisation|organization/i.test(headerText) && /post/i.test(headerText);
}

function parseJobHighlights(html, pageUrl) {
  const $ = cheerio.load(html || '');
  const tables = [];
  $('table').each((_, table) => {
    const $table = $(table);
    if (tableIsHighlights($table)) tables.push($table);
  });

  const rows = [];
  const seen = new Set();

  for (const $table of tables) {
    const headerCells = $table
      .find('tr')
      .first()
      .find('th, td')
      .toArray()
      .map((el) => $(el).text().replace(/\s+/g, ' ').trim());
    const roles = headerRoles(headerCells);

    $table.find('tr').each((idx, tr) => {
      if (idx === 0) return;
      const $tr = $(tr);
      const cells = $tr.find('th, td').toArray();
      if (cells.length < 2) return;

      const values = { organization: '', post: '', method: '', lastDate: null, href: null };
      cells.forEach((el, i) => {
        const text = $(el).text().replace(/\s+/g, ' ').trim();
        const href = $(el).find('a[href]').first().attr('href');
        if (href && !/view\s*more|javascript:/i.test(`${text} ${href}`)) {
          values.href = absoluteUrl(pageUrl, href) || values.href;
        }
        const role = roles[i];
        if (role === 'organization') values.organization = text;
        else if (role === 'post') values.post = text;
        else if (role === 'method') values.method = text;
        else if (role === 'lastDate') values.lastDate = parseDateFromText(text);
        else if (!values.post && text) values.post = text;
      });

      const blob = `${values.organization} ${values.post}`.replace(/\s+/g, ' ').trim();
      if (!blob || /view\s*more|^organisation\b/i.test(blob)) return;

      const officialUrl = values.href || officialUrlForRow({ name: blob }, pageUrl);
      const key = `${blob}|${officialUrl}`;
      if (seen.has(key)) return;
      seen.add(key);

      rows.push({
        title: values.post || blob,
        organization: values.organization || 'As per Employment News notification',
        lastDate: values.lastDate,
        officialUrl,
        href: officialUrl,
        method: values.method || '',
      });
    });
  }

  return rows;
}

function toHighlightRecord(row, source, meta) {
  const rec = toStagingRecord(
    {
      title: row.title,
      organization: row.organization,
      officialUrl: row.officialUrl,
      href: row.href,
      lastDate: row.lastDate,
      sourceUrl: meta.listUrl,
      summary: `${row.title} at ${row.organization}${row.method ? ` (${row.method})` : ''}. Listed in Employment News job highlights. Verify on the official notification.`,
      extraText: row.method || '',
      eligibility: ['Eligibility must be verified on the official site.'],
    },
    source,
    meta
  );
  rec.eligibilityParse = { complete: false };
  return rec;
}

async function collectEmploymentNews(source, ctx) {
  const { runId, collectedAt } = ctx;
  const errors = [];
  const listUrls = candidateUrls(source);
  const records = [];
  let tables = 0;
  let pages = 0;

  for (const listUrl of listUrls) {
    try {
      const { text, url } = await fetchText(listUrl, { delayMs: Number(source.rateLimitMs || 800) });
      const rawName = `highlights-${Buffer.from(listUrl).toString('base64url').slice(0, 32)}.html`;
      saveRaw(source.sourceId, runId, rawName, text);
      pages += 1;
      const parsed = parseJobHighlights(text, url);
      if (parsed.length) tables += 1;
      for (const row of parsed) {
        records.push(
          toHighlightRecord(row, source, {
            collectedAt,
            collectorVersion: 'highlights-v1',
            listUrl: url,
          })
        );
      }
    } catch (err) {
      errors.push({ url: listUrl, message: err.message });
    }
  }

  const unique = dedupeByUrl(records);
  return {
    records: unique,
    errors,
    metrics: {
      listUrls: listUrls.length,
      pages,
      highlightTables: tables,
      written: unique.length,
      pdfs: 0,
    },
  };
}

module.exports = { collectEmploymentNews, parseJobHighlights };
