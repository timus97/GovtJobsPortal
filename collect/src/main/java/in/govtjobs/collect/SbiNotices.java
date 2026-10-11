package in.govtjobs.collect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** SBI career pages publish advertisement blocks that are not always a single anchor. */
final class SbiNotices {

    private SbiNotices() {}

    private static final Pattern ADVERTISEMENT = Pattern.compile("advertisement\\s*no", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADVERTISEMENT_NO = Pattern.compile(
            "advertisement\\s*no[:\\s.]*([A-Z0-9/._-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST = Pattern.compile(
            "last\\s*date(?:\\s*to\\s*apply)?[:\\s]*([0-9]{1,2}[-/.][0-9]{1,2}[-/.]20[0-9]{2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FALLBACK = Pattern.compile("recruit|apply|opening|crpd|advert", Pattern.CASE_INSENSITIVE);

    static List<Map<String, Object>> openings(String html, String pageUrl) {
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element element : document.select("div, li, article, section, tr")) {
            String text = element.text().replaceAll("\\s+", " ").trim();
            if (!ADVERTISEMENT.matcher(text).find() || text.length() > 1200) {
                continue;
            }
            Matcher number = ADVERTISEMENT_NO.matcher(text);
            Matcher last = LAST.matcher(text);
            String title = text.replaceAll("(?i)advertisement\\s*no[:\\s.]*[A-Z0-9/._-]+", " ")
                    .replaceAll("(?i)last\\s*date(?:\\s*to\\s*apply)?[:\\s]*[0-9]{1,2}[-/.][0-9]{1,2}[-/.]20[0-9]{2}", " ")
                    .replaceAll("(?i)apply now", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (title.length() > 220) {
                title = title.substring(0, 220).trim();
            }
            if (title.length() < 8) {
                continue;
            }
            Element apply = null;
            for (Element anchor : element.select("a[href]")) {
                if (anchor.text().matches("(?i).*apply.*")) {
                    apply = anchor;
                    break;
                }
            }
            if (apply == null) {
                apply = element.selectFirst("a[href]");
            }
            String official = pageUrl;
            if (apply != null) {
                String href = apply.absUrl("href");
                if (href.isEmpty()) {
                    href = HtmlLinks.absoluteUrl(pageUrl, apply.attr("href"));
                }
                href = HtmlLinks.preferHttps(pageUrl, href);
                if (href != null && href.regionMatches(true, 0, "https://", 0, 8)) {
                    official = href;
                }
            }
            String key = title + "|" + official;
            if (!seen.add(key)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", title);
            item.put("href", official);
            item.put("officialUrl", official);
            item.put("notificationNo", number.find() ? number.group(1) : null);
            item.put("lastDate", last.find() ? NoticeDates.parseDateFromText(last.group(1)) : NoticeDates.parseDateFromText(text));
            item.put("isPdf", official.matches("(?i).*\\.pdf(\\?|#|$).*"));
            items.add(item);
        }
        if (!items.isEmpty()) {
            return items;
        }
        for (Map<String, Object> link : HtmlLinks.extractLinks(html, pageUrl, true, 40)) {
            String href = String.valueOf(link.get("href"));
            String title = String.valueOf(link.get("title"));
            if (!FALLBACK.matcher(title + " " + href).find() || !seen.add(href)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(link);
            item.put("officialUrl", href);
            item.put("lastDate", NoticeDates.parseDateFromText(title));
            item.put("notificationNo", null);
            items.add(item);
        }
        return items;
    }

    static String selection(String title) {
        String blob = title == null ? "" : title;
        if (blob.matches("(?i).*apprentice.*")) {
            return "apprenticeship";
        }
        if (blob.matches("(?i).*retired.*|.*contract basis.*|.*interview schedule.*")
                && !blob.matches("(?i).*written.*|.*exam.*|.*cbt.*|.*\\bpo\\b.*|.*junior associate.*")) {
            return "contract_interview";
        }
        return "cbt";
    }

    static boolean hasExam(String title) {
        return !"contract_interview".equals(selection(title));
    }
}
