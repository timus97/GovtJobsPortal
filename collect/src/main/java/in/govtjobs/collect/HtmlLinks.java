package in.govtjobs.collect;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Anchor extraction with the same keep and drop rules as {@code scripts/collect/lib/htmlLinks.js}. */
public final class HtmlLinks {

    private HtmlLinks() {}

    private static final Pattern JOB_HREF_RE = Pattern.compile(
            "recruit|vacanc|notification|opening|walk[\\s-]?in|apprentice|advertisement|advt|employment|circular|engagement|consultant|\\.pdf",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JOB_TEXT_RE = Pattern.compile(
            "recruit|vacanc|walk[\\s-]?in|apprentice|notification|opening|consultant|interview|engagement|advt|advertisement|apply online|last date",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF_HREF_RE = Pattern.compile("\\.pdf(\\?|#|$)", Pattern.CASE_INSENSITIVE);

    public static List<Map<String, Object>> extractLinks(String html, String pageUrl) {
        return extractLinks(html, pageUrl, true, 80);
    }

    public static List<Map<String, Object>> extractLinks(String html, String pageUrl, boolean jobLikeOnly, int limit) {
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> links = new ArrayList<>();
        for (Element anchor : document.select("a[href]")) {
            String hrefRaw = anchor.attr("href").trim();
            if (JobLinkQuality.skipHref(hrefRaw)) {
                continue;
            }
            String href = anchor.absUrl("href");
            if (href.isEmpty()) {
                href = absoluteUrl(pageUrl, hrefRaw);
            }
            href = preferHttps(pageUrl, href);
            if (href == null || !href.regionMatches(true, 0, "https://", 0, 8) || seen.contains(href)) {
                continue;
            }
            String text = anchor.text().replaceAll("\\s+", " ").trim();
            String title = text;
            if (title.isEmpty()) {
                title = anchor.attr("title").trim();
            }
            if (title.isEmpty()) {
                title = anchor.attr("aria-label").trim();
            }
            if (title.isEmpty()) {
                title = anchor.select("img[alt]").attr("alt").replaceAll("\\s+", " ").trim();
            }
            if (title.isEmpty()) {
                title = tail(href);
            }
            String row = rowText(anchor);
            boolean weakTitle = title.matches(
                            "(?i)^(english|hindi|download|view|view details?|click here|figure)(\\s*\\(.*\\))?$")
                    || title.matches("(?i)[\\w.+-]+\\.pdf$");
            if (weakTitle
                    && row.length() > title.length()
                    && row.length() <= 400
                    && row.matches("(?i).*(recruit|vacanc|notification|advertisement|walk-?in|post of|requirement of).*")) {
                title = row.length() > 240 ? row.substring(0, 240).trim() : row;
            }
            boolean pdf = PDF_HREF_RE.matcher(href).find() || JobLinkQuality.looksLikeDocumentHref(href);
            if (jobLikeOnly) {
                JobLinkQuality.Score scored = JobLinkQuality.scoreJobLink(Map.of("title", title, "href", href, "isPdf", pdf));
                String blob = title + " " + href;
                if (scored.score() < 2
                        && !JOB_HREF_RE.matcher(blob).find()
                        && !JOB_TEXT_RE.matcher(title).find()
                        && !pdf) {
                    continue;
                }
                if (scored.score() < 0) {
                    continue;
                }
            }
            String clipped = title.length() > 240 ? title.substring(0, 240) : title;
            String textBody = row.length() > clipped.length() ? row : clipped;
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("title", clipped);
            link.put("href", href);
            link.put("text", textBody.length() > 500 ? textBody.substring(0, 500) : textBody);
            link.put("isPdf", pdf);
            seen.add(href);
            links.add(link);
        }
        List<Map<String, Object>> chosen = JobLinkQuality.filterJobLinks(links);
        if (chosen.size() <= limit) {
            return chosen;
        }
        return new ArrayList<>(chosen.subList(0, limit));
    }

    static String preferHttps(String pageUrl, String href) {
        if (href == null || !href.regionMatches(true, 0, "http://", 0, 7)) {
            return href;
        }
        if (pageUrl == null || !pageUrl.regionMatches(true, 0, "https://", 0, 8)) {
            return href;
        }
        if (!JsoupPages.sameHost(pageUrl, href)) {
            return href;
        }
        return "https://" + href.substring("http://".length());
    }

    private static String rowText(Element anchor) {
        Element row = anchor.parent();
        while (row != null) {
            String role = row.attr("role");
            if ("tr".equalsIgnoreCase(row.tagName()) || "row".equalsIgnoreCase(role)) {
                break;
            }
            row = row.parent();
        }
        if (row == null) {
            return "";
        }
        return row.text().replaceAll("\\s+", " ").trim();
    }

    static String absoluteUrl(String base, String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        try {
            String cleaned = href.trim().replace(" ", "%20");
            URI resolved = base == null || base.isBlank() ? URI.create(cleaned) : URI.create(base).resolve(cleaned);
            return resolved.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String tail(String href) {
        String path = href;
        int slash = path.lastIndexOf('/');
        String last = slash >= 0 ? path.substring(slash + 1) : path;
        int query = last.indexOf('?');
        if (query >= 0) {
            last = last.substring(0, query);
        }
        if (last.isEmpty()) {
            return href;
        }
        try {
            return URLDecoder.decode(last, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return last;
        }
    }
}
