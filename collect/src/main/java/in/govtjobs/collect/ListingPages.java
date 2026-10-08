package in.govtjobs.collect;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Listing pages whose notices are not ordinary anchors: Employment News highlight rows,
 * one same-site hop to a careers or notifications URL, and calendar sentences that name a deadline.
 */
final class ListingPages {

    private static final Pattern HOP_SKIP = Pattern.compile(
            "tender|eprocure|result|eligible\\s+candidate|hall\\s?ticket|answer\\s*key|login|sign\\s?in|resume|life\\s+at|recruitment\\s+rules|भर्ती\\s*नियम|/administration|प्रशासनिक",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HOP_KEEP = Pattern.compile(
            "current[-\\s]?opening|vacanc|recruit|भर्ती|अधिसूचना|notification|advertisement|\\badvt\\b|career",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF = Pattern.compile("\\.pdf(\\?|#|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBEDDED_PDF = Pattern.compile("(?i)[\"']([^\"']+\\.pdf)");
    private static final Pattern EMBEDDED_KEEP = Pattern.compile("advert|advt|vacanc|walk-?in|recruit|notification", Pattern.CASE_INSENSITIVE);
    private static final Pattern GO_TO = Pattern.compile("(?i)please go to\\s+(https://[^\\s<\"']+)");
    private static final Pattern DOC_REJECT = Pattern.compile(
            "result|tender|syllabus|\\brules\\b|manual|brochure|organogram|hall\\s?ticket|answer\\s*key|नियम",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROSE = Pattern.compile(
            "(?i)([^\\n]{10,90}(?:closing\\s*date|last\\s*date(?:\\s*to\\s*apply)?)[^\\n]{0,90})");
    private static final Pattern LISTING_NOTICE = Pattern.compile(
            "recruit|vacanc|walk[\\s-]?in|apprentice|advertisement|\\badvt\\b|notification|engagement|consultant|भर्ती|अधिसूचना|opening",
            Pattern.CASE_INSENSITIVE);
    private static final int HIGHLIGHT_CAP = 40;

    private ListingPages() {}

    /** A client-rendered shell, or a body with no anchors. A login form is not a shell. */
    static boolean needsBrowser(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("openid-connect") || lower.contains("g-recaptcha") || lower.contains("hcaptcha")) {
            return false;
        }
        int anchors = countHrefs(lower);
        boolean shell = lower.contains("<app-root")
                || lower.contains("id=\"root\"")
                || lower.contains("id='root'")
                || lower.contains("id=\"__next\"")
                || lower.contains("ng-version");
        if (shell && anchors < 5) {
            return true;
        }
        return anchors == 0 && html.length() < 12_000 && lower.contains("<script");
    }

    /** HAL's shell copied onto another source id is not that organisation's vacancy list. */
    static boolean foreignHalPage(SourceSpec source, String html) {
        if (source == null || source.sourceId().startsWith("psu_hal")) {
            return false;
        }
        return titleOf(html).toLowerCase(Locale.ROOT).contains("hindustan aeronautics");
    }

    static String titleOf(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).title().replaceAll("\\s+", " ").trim();
    }

    /** Same-site listing URLs worth one extra fetch. Highest score first. PDFs stay out. */
    static List<String> hops(String html, String pageUrl) {
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        int anchors = document.select("a[href]").size();
        List<Hop> found = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String here = pathKey(pageUrl);
        for (Element anchor : document.select("a[href]")) {
            String href = absoluteHttps(pageUrl, anchor);
            if (href == null || !seen.add(href) || pathKey(href).equals(here) || PDF.matcher(href).find()) {
                continue;
            }
            int score = hopScore(anchor.text(), href, anchors);
            if (score > 0) {
                found.add(new Hop(href, score));
            }
        }
        Matcher go = GO_TO.matcher(document.text());
        while (go.find()) {
            String href = trimUrl(go.group(1));
            if (href.regionMatches(true, 0, "https://", 0, 8)
                    && JsoupPages.sameHost(pageUrl, href)
                    && seen.add(href)
                    && !pathKey(href).equals(here)) {
                found.add(new Hop(href, 5));
            }
        }
        found.sort(Comparator.comparingInt(Hop::score).reversed());
        List<String> out = new ArrayList<>();
        for (Hop hop : found) {
            if (out.size() == 4) {
                break;
            }
            out.add(hop.href());
        }
        return out;
    }

    /**
     * Employment News JOB HIGHLIGHTS rows. A row with no per-cell link keeps the listing page,
     * which is the official free table. E-paper and other PDFs are not followed.
     */
    static List<Map<String, Object>> highlights(String html, String pageUrl) {
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        List<Map<String, Object>> rows = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Element table : document.select("table")) {
            Element header = table.select("tr").first();
            if (header == null) {
                continue;
            }
            String headerText = header.text().toLowerCase(Locale.ROOT);
            if (!headerText.contains("organ") || !headerText.contains("post")) {
                continue;
            }
            List<Element> heads = header.select("th, td");
            int orgCol = -1;
            int postCol = -1;
            int methodCol = -1;
            int lastCol = -1;
            for (int i = 0; i < heads.size(); i++) {
                String cell = heads.get(i).text().toLowerCase(Locale.ROOT);
                if (cell.contains("organ")) {
                    orgCol = i;
                } else if (cell.contains("post")) {
                    postCol = i;
                } else if (cell.contains("method")) {
                    methodCol = i;
                } else if (cell.contains("last")) {
                    lastCol = i;
                }
            }
            if (postCol < 0 || lastCol < 0) {
                continue;
            }
            List<Element> body = table.select("tr");
            for (int r = 1; r < body.size() && rows.size() < HIGHLIGHT_CAP; r++) {
                List<Element> cells = body.get(r).select("th, td");
                if (cells.size() <= Math.max(postCol, lastCol)) {
                    continue;
                }
                String org = textAt(cells, orgCol);
                String post = textAt(cells, postCol);
                String method = textAt(cells, methodCol);
                String last = textAt(cells, lastCol);
                String blob = (org + " " + post).replaceAll("\\s+", " ").trim();
                if (post.length() < 3 || blob.matches("(?i).*view\\s*more.*|^organisation\\b.*")) {
                    continue;
                }
                String iso = NoticeDates.parseDateFromText(last);
                if (iso == null) {
                    continue;
                }
                String href = rowHref(cells, pageUrl);
                String title = post;
                if (method.matches("(?i).*(recruit|deput|engag|contract|walk).*")) {
                    title = post + " — " + method;
                }
                if (!org.isBlank()) {
                    title = title + " — " + org;
                }
                if (title.length() > 220) {
                    title = title.substring(0, 220).trim();
                }
                String key = (org + "|" + post + "|" + iso).toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", title);
                item.put("href", href);
                item.put("organization", org.isBlank() ? null : org);
                item.put("lastDate", iso);
                item.put("text", blob + (method.isBlank() ? "" : " " + method) + " " + last);
                item.put(
                        "summary",
                        post
                                + (org.isBlank() ? "" : " at " + org)
                                + (method.isBlank() ? "" : " (" + method + ")")
                                + ". Listed in Employment News job highlights. Verify on the official notification.");
                item.put("extraText", method);
                rows.add(item);
            }
        }
        return rows;
    }

    /**
     * Advertisement rows whose only control is a hash or icon. The listing page is the https URL.
     * A row that already has an http(s) or pdf link is left to the normal extractor.
     */
    static List<Map<String, Object>> advtRowsWithoutLinks(String html, String pageUrl) {
        if (pageUrl == null || !pageUrl.regionMatches(true, 0, "https://", 0, 8)) {
            return List.of();
        }
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl);
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Element row : document.select("table tr")) {
            String text = row.text().replaceAll("\\s+", " ").trim();
            if (text.length() < 25
                    || text.length() > 420
                    || !text.matches("(?i).*(advt\\.?\\s*no|advertisement\\s*no\\.?).*")
                    || !text.matches(".*\\d.*")) {
                continue;
            }
            if (text.matches("(?i).*(result|selected candidate|screening schedule|call letter).*")) {
                continue;
            }
            boolean linked = false;
            for (Element anchor : row.select("a[href]")) {
                String href = anchor.attr("href").trim();
                if (href.regionMatches(true, 0, "http", 0, 4) || PDF.matcher(href).find()) {
                    linked = true;
                    break;
                }
            }
            if (linked || !seen.add(text.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String title = text.length() > 220 ? text.substring(0, 220).trim() : text;
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("title", title);
            link.put("href", pageUrl);
            link.put("text", text);
            link.put("isPdf", false);
            out.add(link);
            if (out.size() == 12) {
                break;
            }
        }
        return out;
    }

    /** Advertisement PDFs that sit in a script template, so they are not real anchors until the browser runs. */
    static List<Map<String, Object>> embeddedAdvertPdfs(String html, String pageUrl) {
        if (html == null || html.isBlank() || pageUrl == null || !pageUrl.regionMatches(true, 0, "https://", 0, 8)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Matcher matcher = EMBEDDED_PDF.matcher(html);
        while (matcher.find() && out.size() < 15) {
            String raw = matcher.group(1).trim();
            if (!EMBEDDED_KEEP.matcher(raw).find() || raw.matches("(?i).*(result|tender|cancellation|corrigendum).*")) {
                continue;
            }
            String href = HtmlLinks.absoluteUrl(pageUrl, raw);
            href = HtmlLinks.preferHttps(pageUrl, href);
            if (href == null || !href.regionMatches(true, 0, "https://", 0, 8) || !JsoupPages.sameHost(pageUrl, href) || !seen.add(href)) {
                continue;
            }
            String file = raw.substring(raw.lastIndexOf('/') + 1).replaceAll("(?i)\\.pdf$", "").replace('-', ' ').replace('_', ' ');
            if (file.length() < 8) {
                continue;
            }
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("title", file.length() > 180 ? file.substring(0, 180) : file);
            link.put("href", href);
            link.put("text", file);
            link.put("isPdf", true);
            out.add(link);
        }
        return out;
    }

    /** PDFs on a recruitment listing whose titles are not English notice phrases. Bare /careers is not enough. */
    static boolean documentListing(String url) {
        String path = decodedPath(url).toLowerCase(Locale.ROOT);
        if (path.isBlank() || "/".equals(path) || path.matches(".*/(careers?|jobs?)/?")) {
            return false;
        }
        return path.matches("(?s).*(recruit|vacanc|current-opening|notification|advertisement|advt|भर्ती|अधिसूचना).*");
    }

    static List<Map<String, Object>> listingDocuments(String html, String pageUrl) {
        if (!documentListing(pageUrl)) {
            return List.of();
        }
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Element anchor : document.select("a[href]")) {
            String href = absoluteHttps(pageUrl, anchor);
            if (href == null || !PDF.matcher(href).find() || !seen.add(href)) {
                continue;
            }
            String title = anchor.text().replaceAll("\\s+", " ").trim();
            if (title.isEmpty()) {
                title = anchor.attr("title").trim();
            }
            if (title.length() < 8 || title.length() > 240) {
                continue;
            }
            if (DOC_REJECT.matcher(title).find() || !LISTING_NOTICE.matcher(title + " " + href).find()) {
                continue;
            }
            JobLinkQuality.Score scored = JobLinkQuality.scoreJobLink(Map.of("title", title, "href", href, "isPdf", true));
            if (scored.score() < 0 || scored.score() >= 2) {
                continue;
            }
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("title", title);
            link.put("href", href);
            link.put("text", title);
            link.put("isPdf", true);
            out.add(link);
            if (out.size() == 15) {
                break;
            }
        }
        return out;
    }

    /** A deadline sentence on a calendar page that has no dated exam table. */
    static List<Map<String, Object>> calendarProse(String html, String pageUrl) {
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        String official = pageUrl == null ? "" : pageUrl;
        if (JobLinkQuality.isHomepageUrl(official)) {
            for (String hop : hops(html, pageUrl)) {
                String path = decodedPath(hop).toLowerCase(Locale.ROOT);
                if (path.contains("notif") || path.contains("guideline") || path.contains("apply")) {
                    official = hop;
                    break;
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        LocalDate cutoff = LocalDate.now().minusMonths(18);
        String pageText = document.text().replaceAll("\\s+", " ");
        Matcher prose = PROSE.matcher(pageText);
        while (prose.find() && rows.size() < 5) {
            String text = prose.group(1).replaceAll("\\s+", " ").trim();
            if (!text.matches("(?i).*(registration|application|apply|exam|recruit|walk-?in).*")) {
                continue;
            }
            int registrationAt = text.toLowerCase(Locale.ROOT).lastIndexOf("regular registration");
            if (registrationAt < 0) {
                registrationAt = text.toLowerCase(Locale.ROOT).lastIndexOf("closing date");
            }
            if (registrationAt > 0) {
                text = text.substring(registrationAt).trim();
            }
            String iso = NoticeDates.findLastDateHint(text);
            if (iso == null) {
                continue;
            }
            LocalDate parsed;
            try {
                parsed = LocalDate.parse(iso);
            } catch (RuntimeException ex) {
                continue;
            }
            if (parsed.isBefore(cutoff) || !seen.add(iso + "|" + text.substring(0, Math.min(48, text.length())))) {
                continue;
            }
            String title = text.length() > 170 ? text.substring(0, 170).trim() : text;
            if (!title.toLowerCase(Locale.ROOT).contains("notification")) {
                title = "Notification: " + title;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", title);
            item.put("href", official);
            item.put("officialUrl", official);
            item.put("lastDate", iso);
            item.put("summary", text + " Verify on the official site.");
            item.put("text", text);
            rows.add(item);
        }
        return rows;
    }

    private static int hopScore(String text, String href, int anchors) {
        String blob = (text + " " + decoded(href)).replaceAll("\\s+", " ").trim();
        if (HOP_SKIP.matcher(blob).find()) {
            return -1;
        }
        String lower = blob.toLowerCase(Locale.ROOT);
        if (lower.contains("current-opening") || lower.contains("current opening") || lower.contains("vacanc")) {
            return 5;
        }
        if (lower.contains("recruit") || lower.contains("भर्ती") || lower.contains("अधिसूचना")) {
            return 4;
        }
        if (lower.contains("notification") || lower.contains("advertisement") || lower.contains("advt")) {
            return 3;
        }
        if (lower.contains("career")) {
            return 2;
        }
        if (anchors <= 8 && text != null && text.matches("(?i).*\\bwebsite\\b.*") && text.length() < 48) {
            return 1;
        }
        if (!HOP_KEEP.matcher(blob).find()) {
            return 0;
        }
        return 2;
    }

    /** Highlight rows link out to the employer's notice. That host is not Employment News. */
    private static String rowHref(List<Element> cells, String pageUrl) {
        for (Element cell : cells) {
            for (Element anchor : cell.select("a[href]")) {
                String href = anchor.absUrl("href");
                if (href == null || href.isBlank()) {
                    href = HtmlLinks.absoluteUrl(pageUrl, anchor.attr("href"));
                }
                href = HtmlLinks.preferHttps(pageUrl, href);
                if (href == null || !href.regionMatches(true, 0, "https://", 0, 8) || PDF.matcher(href).find()) {
                    continue;
                }
                String blob = anchor.text() + " " + href;
                if (blob.matches("(?i).*(view\\s*more|Home\\.aspx#|AllJobs\\.aspx#).*")) {
                    continue;
                }
                return href;
            }
        }
        return pageUrl == null ? "" : pageUrl;
    }

    private static String absoluteHttps(String pageUrl, Element anchor) {
        String href = anchor.absUrl("href");
        if (href == null || href.isBlank()) {
            href = HtmlLinks.absoluteUrl(pageUrl, anchor.attr("href"));
        }
        href = HtmlLinks.preferHttps(pageUrl, href);
        if (href == null || !href.regionMatches(true, 0, "https://", 0, 8)) {
            return null;
        }
        if (pageUrl != null && !JsoupPages.sameHost(pageUrl, href)) {
            return null;
        }
        return trimUrl(href);
    }

    private static String trimUrl(String href) {
        return href.replaceAll("[).,]+$", "");
    }

    private static String textAt(List<Element> cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return "";
        }
        return cells.get(index).text().replaceAll("\\s+", " ").trim();
    }

    private static int countHrefs(String lowerHtml) {
        int count = 0;
        int from = 0;
        while (from >= 0 && count < 8) {
            from = lowerHtml.indexOf("<a", from);
            if (from < 0) {
                break;
            }
            count++;
            from += 2;
        }
        return count;
    }

    private static String pathKey(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return host + path.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return url;
        }
    }

    private static String decodedPath(String url) {
        String path = pathKey(url);
        int slash = path.indexOf('/');
        String only = slash >= 0 ? path.substring(slash) : path;
        return decoded(only);
    }

    private static String decoded(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private record Hop(String href, int score) {}
}
