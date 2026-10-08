const cheerio = require('cheerio');
const { scoreJobLink, looksLikeDocumentHref, GARBAGE_HREF_RE, isKeepableJobLink } = require('./jobLinkQuality');

const JOB_HREF_RE =
  /recruit|vacanc|notification|opening|walk[\s-]?in|apprentice|advertisement|advt|employment|circular|engagement|consultant|\.pdf/i;

const JOB_TEXT_RE =
  /recruit|vacanc|walk[\s-]?in|apprentice|notification|opening|consultant|interview|engagement|advt|advertisement|apply online|last date/i;

const SKIP_HREF_RE = GARBAGE_HREF_RE;

function absoluteUrl(base, href) {
  if (!href) return null;
  try {
    return new URL(String(href).trim().replace(/ /g, '%20'), base).href;
  } catch {
    return null;
  }
}

function extractLinks(html, pageUrl, options = {}) {
  const { jobLikeOnly = true, limit = 80 } = options;
  const $ = cheerio.load(html || '');
  const seen = new Set();
  const links = [];

  $('a[href]').each((_, el) => {
    const hrefRaw = ($(el).attr('href') || '').trim();
    if (!hrefRaw || SKIP_HREF_RE.test(hrefRaw)) return;
    const href = absoluteUrl(pageUrl, hrefRaw);
    if (!href || !/^https?:\/\//i.test(href)) return;
    if (seen.has(href)) return;

    const text = $(el).text().replace(/\s+/g, ' ').trim();
    const alt = $(el).find('img[alt]').first().attr('alt') || '';
    let title =
      text ||
      $(el).attr('title') ||
      $(el).attr('aria-label') ||
      alt.replace(/\s+/g, ' ').trim() ||
      decodeURIComponent(href.split('/').filter(Boolean).pop() || href);
    const row = $(el).closest('tr').text().replace(/\s+/g, ' ').trim();
    const weakTitle =
      /^(english|hindi|download|view|view details?|click here|figure)(\s*\(.*\))?$/i.test(title) ||
      /[\w.+-]+\.pdf$/i.test(title);
    if (
      weakTitle &&
      row.length > String(title).length &&
      row.length <= 400 &&
      /recruit|vacanc|notification|advertisement|walk-?in|post of|requirement of/i.test(row)
    ) {
      title = row.slice(0, 240).trim();
    }

    const isPdf = /\.pdf(\?|#|$)/i.test(href) || looksLikeDocumentHref(href);
    const blob = `${title} ${href}`;
    if (jobLikeOnly) {
      const scored = scoreJobLink({ title, href, isPdf });
      if (scored.score < 2 && !JOB_HREF_RE.test(blob) && !JOB_TEXT_RE.test(title) && !isPdf) {
        return;
      }
      if (scored.score < 0) return;
    }

    seen.add(href);
    links.push({
      title: String(title).slice(0, 240),
      href,
      text: String(title).slice(0, 500),
      isPdf,
    });
  });

  const keepable = links.filter((link) => isKeepableJobLink(link));
  return keepable.slice(0, limit);
}

function extractPageTitle(html) {
  const cheerio = require('cheerio');
  const $ = cheerio.load(html || '');
  return $('title').first().text().replace(/\s+/g, ' ').trim() || '';
}

module.exports = {
  extractLinks,
  extractPageTitle,
  absoluteUrl,
  JOB_HREF_RE,
  JOB_TEXT_RE,
};
