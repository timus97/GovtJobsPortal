/**
 * Drop nav chrome, tenders, employer actions, and homepage "jobs".
 * Keep specific recruitment notices and PDF advertisements.
 */

const GARBAGE_TITLE_EXACT_RE =
  /^(home|welcome|careers?|recruitments?|current openings?|job openings?|notifications?|what'?s new|latest updates?|download|login|log in|sign\s?up|sign in)$/i;

const GARBAGE_TITLE_RE =
  /skip to|know more|discover more|click here|read more|view all|apply now|apply online|हिंदी|circulars?|brochure|handbook|updated on|working at|post new jobs?|national career service|stqc certificate|\bingots?\b|specification|postal ballot|tds guideline|recruitment policy|integrity pact|pidpi|public awareness|language\?|returnurl|corporate plan|dashboard on backlog|press note|syllabus(?!\s+for)/i;

const GARBAGE_HREF_RE =
  /javascript:|mailto:|tel:|#($|\?)|facebook|twitter|linkedin|instagram|youtube|whatsapp|privacy|terms|cookie|sitemap|authenticate|user-management\/login|\/login(\?|$)|signup|employer|job-post|jobposting|job-fair|jobfair|tender|eprocure|cppp|rti(\/|$)|annual.?report|investor|csr(\/|$)|vigilance|grievance|contact-us|about-us|media.?galler|press.?release|detail\?assetentry|recruitment.?policy|\/brochure|\/mou(\/|$)|brsr|pidpi|vendor.?list|citizen.?charter|postal.?ballot|tds.?guideline|Home\.aspx#|AllJobs\.aspx#/i;

const NOTICE_HREF_RE =
  /notif|advert|advt|vacanc|recruit|walk[\s-]?in|apprentice|engagement|advertisement|corrigend|getfile|viewdoc|downloadfile/i;

const NOTICE_TEXT_RE =
  /recruitment|vacancy|vacancies|walk[\s-]?in|apprentice|notification|advertisement|advt\.?\s*no|engagement|consultant|contract post|apply online|last date|no\.?\s*of\s*posts?|applications invited|invites applications/i;

const TENDER_RE =
  /\btender\b|\bnit\b|\bnit_|e-nit\b|\beol\b|\brfp\b|request for proposal|contract award|bid document|notice inviting tender|e-tender/i;

const EMPLOYER_RE = /post new jobs?|job fair|for employers|post a job|employer login/i;

const DOC_HREF_RE =
  /\.pdf(\?|#|$)|\/download|getfile|viewdoc|viewfile|openfile|downloadfile|documents?\/|uploads?\/.*\.(pdf|docx?)(\?|#|$)/i;

function hostnameOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '').toLowerCase();
  } catch {
    return '';
  }
}

function pathnameOf(url) {
  try {
    return new URL(url).pathname.replace(/\/+$/, '') || '/';
  } catch {
    return '';
  }
}

function isHomepageUrl(url) {
  const path = pathnameOf(url);
  if (path === '/' || path === '') {
    try {
      const u = new URL(url);
      return !u.search || u.search.length < 3;
    } catch {
      return true;
    }
  }
  return false;
}

function isIndexOnlyUrl(url) {
  const path = pathnameOf(url).toLowerCase();
  return /\/(careers?|career-page|recruitment|jobs?|openings?|notifications?|current-openings?)$/i.test(
    path
  ) || /\/(policy|brochure|mou|brsr|pidpi|vendor-list|citizen-charter)(\/|$)/i.test(path);
}

function looksLikeDocumentHref(url) {
  return DOC_HREF_RE.test(url || '');
}

function scoreJobLink(link = {}) {
  const title = String(link.title || link.text || '').replace(/\s+/g, ' ').trim();
  const href = String(link.href || link.officialUrl || '');
  const blob = `${title} ${href}`;
  let score = 0;
  const reasons = [];

  if (!href || !/^https?:\/\//i.test(href)) return { score: -10, reasons: ['bad_url'] };
  if (GARBAGE_HREF_RE.test(href) || GARBAGE_HREF_RE.test(title)) {
    return { score: -8, reasons: ['garbage_href'] };
  }
  if (EMPLOYER_RE.test(blob)) return { score: -8, reasons: ['employer_action'] };
  if (GARBAGE_TITLE_EXACT_RE.test(title) || GARBAGE_TITLE_RE.test(title)) {
    return { score: -8, reasons: ['nav_title'] };
  }
  if (TENDER_RE.test(blob) && !NOTICE_TEXT_RE.test(title)) {
    return { score: -6, reasons: ['tender'] };
  }
  if (isHomepageUrl(href)) return { score: -6, reasons: ['homepage'] };

  if (NOTICE_TEXT_RE.test(title)) {
    score += 3;
    reasons.push('notice_title');
  }
  if (NOTICE_HREF_RE.test(href)) {
    score += 2;
    reasons.push('notice_href');
  }
  if ((looksLikeDocumentHref(href) || link.isPdf) && NOTICE_TEXT_RE.test(title)) {
    score += 2;
    reasons.push('document');
  }
  if (/advt|notification no|vacancy no|walk-in/i.test(title)) {
    score += 2;
    reasons.push('advt');
  }
  if (isIndexOnlyUrl(href) && !NOTICE_TEXT_RE.test(title) && !looksLikeDocumentHref(href)) {
    score -= 4;
    reasons.push('index_only');
  }
  if (title.length < 4) score -= 2;

  return { score, reasons };
}

function isKeepableJobLink(link, source) {
  const { score } = scoreJobLink(link);
  if (score < 2) return false;
  const href = link.href || link.officialUrl || '';
  if (source && source.baseUrl && hostnameOf(href) && hostnameOf(source.baseUrl)) {
    /* allow official hosts and common apply hosts */
  }
  if (isHomepageUrl(href)) return false;
  return true;
}

function filterJobLinks(links, source) {
  const out = [];
  const seen = new Set();
  for (const link of links || []) {
    const href = String(link.href || '').split('#')[0];
    if (!href || seen.has(href.toLowerCase())) continue;
    if (!isKeepableJobLink({ ...link, href }, source)) continue;
    seen.add(href.toLowerCase());
    out.push({ ...link, href });
  }
  return out;
}

const REJECT_PDF_RE =
  /result of|travel reimbursement|registration flow|user manual|specification|brochure|integrity pact|handbook|flowchart|employer portal|login flow|ingot|pidpi|public awareness|quality management|stqc/i;

const REQUIRE_PDF_RE =
  /advertisement for|applications are invited|invites applications|walk-?in interview|engagement of|no\.?\s*of\s*posts?|vacancy notification|recruitment notice|apply online/i;

function isRecruitmentPdfText(text) {
  const head = String(text || '').slice(0, 5000);
  if (!head.trim()) return false;
  if (TENDER_RE.test(head) && !REQUIRE_PDF_RE.test(head)) return false;
  if (REJECT_PDF_RE.test(head) && !REQUIRE_PDF_RE.test(head)) return false;
  return REQUIRE_PDF_RE.test(head);
}

function isGarbageJob(job) {
  const title = String(job.title || '').trim();
  if (GARBAGE_TITLE_EXACT_RE.test(title) || GARBAGE_TITLE_RE.test(title)) {
    return { garbage: true, reasons: ['nav_title'] };
  }
  const href = job.officialUrl || '';
  if (isHomepageUrl(href)) return { garbage: true, reasons: ['homepage'] };
  const isPdf = /\.pdf(\?|#|$)/i.test(href);
  if (isIndexOnlyUrl(href) && !isPdf && !NOTICE_TEXT_RE.test(title)) {
    return { garbage: true, reasons: ['index_only'] };
  }
  const link = { title, href, isPdf };
  const { score, reasons } = scoreJobLink(link);
  if (score < 0) return { garbage: true, reasons };
  const isScrape = /scrape|playwright|pdf|highlights/i.test(String(job.collectorVersion || ''));
  if (isScrape && score < 2) return { garbage: true, reasons };
  return { garbage: false, reasons };
}

module.exports = {
  GARBAGE_TITLE_RE,
  GARBAGE_TITLE_EXACT_RE,
  GARBAGE_HREF_RE,
  NOTICE_TEXT_RE,
  TENDER_RE,
  scoreJobLink,
  isKeepableJobLink,
  filterJobLinks,
  isHomepageUrl,
  isIndexOnlyUrl,
  looksLikeDocumentHref,
  isRecruitmentPdfText,
  isGarbageJob,
};
