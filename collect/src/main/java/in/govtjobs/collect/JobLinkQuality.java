package in.govtjobs.collect;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Same link rejection rules as {@code scripts/collect/lib/jobLinkQuality.js}.
 * Process uses {@link #isGarbageJob} before a row can be published.
 */
public final class JobLinkQuality {

    private JobLinkQuality() {}

    private static final Pattern GARBAGE_TITLE_EXACT_RE = Pattern.compile(
            "^(home|welcome|careers?|recruitments?(?:\\s*›)?|archives|register|gallery|login(?:/\\s*apply)?|norms\\s*&\\s*formats|current openings?|job openings?|notifications?|what'?s new|latest updates?|download|login|log in|sign\\s?up|sign in|vacancies|current vacancies|recruitment notices|apprenticeship opportunities|current recruitments|view advertisement|view details?|click here(?: to (?:read|view|download)(?: more)?)?|tentative vacanc(?:y|ies)|recruitment\\s+भरती|advertisements?\\s*/\\s*notifications?(?:\\s*/\\s*corrigendums?)?)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GARBAGE_TITLE_RE = Pattern.compile(
            "skip (?:to|this)|know more|discover more|read more|view all|apply now|apply online|हिंदी|circulars?|brochure|handbook|updated on|working at|post new jobs?|national career service|stqc certificate|\\bingots?\\b|specification|postal ballot|tds guideline|recruitment policy|integrity pact|pidpi|public awareness|public interest disclosure|language\\?|returnurl|corporate plan|dashboard on backlog|press note|syllabus(?!\\s+for)|advertise on|for advertisers|advertisement policy|organogram|purchase manual|cda rules|vendor registration|cmd interviews|bond series|debenture trustee|cut-?off marks|result of|results of|recruitment results|provisionally selected|selected candidates|shortlisted|enlistment of|office premises|candidate login|travel reimbursement|web advertisement|fake advertisement|fake websites|provisional result|cancellation of advertisement|withdrawal of advertisement|notice of selection|gazette notification|vacancy dashboard|bed occupancy|recruitment regulations|result for|call letter|appointment letter|regarding interview|रिक्त प्रपत्र|blank application|download marksheet|download legacy|curriculum template|प्रशासनिक|पदोन्नति|disqualified contractor|list of eligible|admission in|\\{\\{",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GARBAGE_HREF_RE = Pattern.compile(
            "javascript:|mailto:|tel:|#($|\\?)|facebook|twitter|linkedin|instagram|youtube|whatsapp|privacy|terms|cookie|sitemap|authenticate|user-management/login|/login(\\?|$)|signup|employer|job-post|jobposting|job-fair|jobfair|tender|eprocure|cppp|rti(/|$)|annual.?report|investor|csr(/|$)|vigilance|grievance|contact-us|about-us|media.?galler|press.?release|detail\\?assetentry|recruitment.?policy|/brochure|/mou(/|$)|brsr|pidpi|vendor.?list|citizen.?charter|postal.?ballot|tds.?guideline|Home\\.aspx#|AllJobs\\.aspx#|applicationblank|blank.?form|selectedcandidates|cancellation",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NOTICE_HREF_RE = Pattern.compile(
            "notif|advert|advt|vacanc|recruit|walk[\\s-]?in|apprentice|engagement|advertisement|corrigend|getfile|viewdoc|downloadfile",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NOTICE_TEXT_RE = Pattern.compile(
            "recruitment|vacancy|vacancies|walk[\\s-]?in|apprentice|notification|advertisement|advt\\.?\\s*no|engagement|consultant|contract post|apply online|last date|no\\.?\\s*of\\s*posts?|applications invited|invites applications",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TENDER_RE = Pattern.compile(
            "\\btender\\b|\\bnit\\b|\\bnit_|e-nit\\b|\\beol\\b|\\brfp\\b|request for proposal|contract award|bid document|notice inviting tender|e-tender",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMPLOYER_RE = Pattern.compile(
            "post new jobs?|job fair|for employers|post a job|employer login",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DOC_HREF_RE = Pattern.compile(
            "\\.pdf(\\?|#|$)|/download|getfile|viewdoc|viewfile|openfile|downloadfile|documents?/|uploads?/.*\\.(pdf|docx?)(\\?|#|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PDF_HREF_RE = Pattern.compile("\\.pdf(\\?|#|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern INDEX_TAIL_RE = Pattern.compile(
            "/(careers?|career-page|recruitment|jobs?|openings?|notifications?|current-openings?|vacancies)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INDEX_POLICY_RE = Pattern.compile(
            "/(policy|brochure|mou|brsr|pidpi|vendor-list|citizen-charter)(/|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ADVT_RE = Pattern.compile(
            "advt|notification no|vacancy no|walk-in", Pattern.CASE_INSENSITIVE);

    private static final Pattern REJECT_PDF_RE = Pattern.compile(
            "result of|travel reimbursement|registration flow|user manual|specification|brochure|integrity pact|handbook|flowchart|employer portal|login flow|ingot|pidpi|public awareness|quality management|stqc",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REQUIRE_PDF_RE = Pattern.compile(
            "advertisement for|applications are invited|invites applications|walk-?in interview|engagement of|no\\.?\\s*of\\s*posts?|vacancy notification|recruitment notice|apply online",
            Pattern.CASE_INSENSITIVE);

    public record Score(int score, List<String> reasons) {}

    public record Garbage(boolean garbage, List<String> reasons) {}

    public static Score scoreJobLink(Map<String, ?> link) {
        String title = text(link == null ? null : firstTruthy(link.get("title"), link.get("text")))
                .replaceAll("\\s+", " ")
                .trim();
        String href = text(link == null ? null : firstTruthy(link.get("href"), link.get("officialUrl")));
        String blob = title + " " + href;
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (href.isEmpty() || !href.matches("(?i)^https?://.*")) {
            return new Score(-10, List.of("bad_url"));
        }
        if (GARBAGE_HREF_RE.matcher(href).find() || GARBAGE_HREF_RE.matcher(title).find()) {
            return new Score(-8, List.of("garbage_href"));
        }
        if (EMPLOYER_RE.matcher(blob).find()) {
            return new Score(-8, List.of("employer_action"));
        }
        if (GARBAGE_TITLE_EXACT_RE.matcher(title).matches() || GARBAGE_TITLE_RE.matcher(title).find()) {
            return new Score(-8, List.of("nav_title"));
        }
        if (TENDER_RE.matcher(blob).find() && !NOTICE_TEXT_RE.matcher(title).find()) {
            return new Score(-6, List.of("tender"));
        }
        if (isHomepageUrl(href)) {
            return new Score(-6, List.of("homepage"));
        }

        if (NOTICE_TEXT_RE.matcher(title).find()) {
            score += 3;
            reasons.add("notice_title");
        }
        if (NOTICE_HREF_RE.matcher(href).find()) {
            score += 2;
            reasons.add("notice_href");
        }
        boolean document = looksLikeDocumentHref(href) || (link != null && Boolean.TRUE.equals(link.get("isPdf")));
        if (document && NOTICE_TEXT_RE.matcher(title).find()) {
            score += 2;
            reasons.add("document");
        }
        if (ADVT_RE.matcher(title).find()) {
            score += 2;
            reasons.add("advt");
        }
        if (isIndexOnlyUrl(href) && !NOTICE_TEXT_RE.matcher(title).find() && !looksLikeDocumentHref(href)) {
            score -= 4;
            reasons.add("index_only");
        }
        if (title.length() < 4) {
            score -= 2;
        }
        return new Score(score, List.copyOf(reasons));
    }

    public static Garbage isGarbageJob(Map<String, ?> job) {
        String title = text(job == null ? null : job.get("title")).trim();
        if (GARBAGE_TITLE_EXACT_RE.matcher(title).matches() || GARBAGE_TITLE_RE.matcher(title).find()) {
            return new Garbage(true, List.of("nav_title"));
        }
        String href = text(job == null ? null : job.get("officialUrl"));
        if (isHomepageUrl(href)) {
            return new Garbage(true, List.of("homepage"));
        }
        boolean isPdf = PDF_HREF_RE.matcher(href).find();
        if (isIndexOnlyUrl(href) && !isPdf && !NOTICE_TEXT_RE.matcher(title).find()) {
            return new Garbage(true, List.of("index_only"));
        }
        Score scored = scoreJobLink(Map.of("title", title, "href", href, "isPdf", isPdf));
        if (scored.score() < 0) {
            return new Garbage(true, scored.reasons());
        }
        String version = text(job == null ? null : job.get("collectorVersion"));
        boolean scrape = Pattern.compile("scrape|playwright|pdf|highlights", Pattern.CASE_INSENSITIVE)
                .matcher(version)
                .find();
        if (scrape && scored.score() < 2) {
            return new Garbage(true, scored.reasons());
        }
        return new Garbage(false, scored.reasons());
    }

    public static boolean isKeepableJobLink(Map<String, ?> link) {
        if (scoreJobLink(link).score() < 2) {
            return false;
        }
        String href = text(link == null ? null : firstTruthy(link.get("href"), link.get("officialUrl")));
        return !isHomepageUrl(href);
    }

    public static List<Map<String, Object>> filterJobLinks(List<? extends Map<String, ?>> links) {
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (links == null) {
            return out;
        }
        for (Map<String, ?> link : links) {
            if (link == null) {
                continue;
            }
            String href = text(link.get("href")).split("#", 2)[0];
            String key = href.toLowerCase(Locale.ROOT);
            if (href.isEmpty() || seen.contains(key)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            link.forEach((name, value) -> copy.put(String.valueOf(name), value));
            copy.put("href", href);
            if (!isKeepableJobLink(copy)) {
                continue;
            }
            seen.add(key);
            out.add(copy);
        }
        return out;
    }

    /** A brochure, tender, or result sheet. A real vacancy notice is not rejected. */
    public static boolean rejectedDocument(String text) {
        String head = text == null ? "" : text.substring(0, Math.min(text.length(), 5000));
        if (head.isBlank()) {
            return false;
        }
        boolean required = REQUIRE_PDF_RE.matcher(head).find();
        if (required) {
            return false;
        }
        return TENDER_RE.matcher(head).find() || REJECT_PDF_RE.matcher(head).find();
    }

    public static boolean isRecruitmentPdfText(String text) {
        String head = text == null ? "" : text.substring(0, Math.min(text.length(), 5000));
        if (head.isBlank()) {
            return false;
        }
        boolean required = REQUIRE_PDF_RE.matcher(head).find();
        if (TENDER_RE.matcher(head).find() && !required) {
            return false;
        }
        if (REJECT_PDF_RE.matcher(head).find() && !required) {
            return false;
        }
        return required;
    }

    static boolean skipHref(String hrefRaw) {
        return hrefRaw == null || hrefRaw.isBlank() || GARBAGE_HREF_RE.matcher(hrefRaw).find();
    }

    static boolean isHomepageUrl(String url) {
        String path = pathnameOf(url);
        if ("/".equals(path) || path.isEmpty()) {
            URI uri = parse(url);
            if (uri == null) {
                return true;
            }
            String query = uri.getRawQuery();
            return query == null || query.isEmpty() || query.length() + 1 < 3;
        }
        return false;
    }

    static boolean isIndexOnlyUrl(String url) {
        String path = pathnameOf(url).toLowerCase(Locale.ROOT);
        return INDEX_TAIL_RE.matcher(path).find() || INDEX_POLICY_RE.matcher(path).find();
    }

    static boolean looksLikeDocumentHref(String url) {
        return DOC_HREF_RE.matcher(url == null ? "" : url).find();
    }

    private static String pathnameOf(String url) {
        URI uri = parse(url);
        if (uri == null || uri.getScheme() == null) {
            return "";
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        path = path.replaceAll("/+$", "");
        return path.isEmpty() ? "/" : path;
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return new URI(url);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static Object firstTruthy(Object a, Object b) {
        if (jsTruthy(a)) {
            return a;
        }
        if (jsTruthy(b)) {
            return b;
        }
        return null;
    }

    private static boolean jsTruthy(Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof CharSequence s) {
            return !s.isEmpty();
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        return true;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
