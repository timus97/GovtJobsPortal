const cheerio = require('cheerio');

const JOB_HREF_RE =
  /career|recruit|vacanc|notification|opening|walk[\s-]?in|apprentice|advertisement|advt|employment|job|apply|circular|engagement|consultant|contract|\.pdf/i;

const JOB_TEXT_RE =
  /recruit|vacanc|walk[\s-]?in|apprentice|notification|opening|consultant|contract|interview|engagement|post|advt|advertisement|apply online|job|career/i;

const SKIP_HREF_RE =
  /javascript:|#$|mailto:|tel:|facebook|twitter|linkedin|instagram|youtube|whatsapp|login|signup|privacy|terms|cookie/i;

function absoluteUrl(base, href) {
  if (!href) return null;
  try {
    return new URL(href, base).href;
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
    const title =
      text ||
      $(el).attr('title') ||
      $(el).attr('aria-label') ||
      decodeURIComponent(href.split('/').filter(Boolean).pop() || href);

    const blob = `${title} ${href}`;
    if (jobLikeOnly && !JOB_HREF_RE.test(blob) && !JOB_TEXT_RE.test(title)) {
      return;
    }

    seen.add(href);
    links.push({
      title: String(title).slice(0, 240),
      href,
      text: String(title).slice(0, 500),
      isPdf: /\.pdf(\?|#|$)/i.test(href),
    });
  });

  return links.slice(0, limit);
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
