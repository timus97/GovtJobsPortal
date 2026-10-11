package in.govtjobs.collect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Exam-calendar PDF links and the date rows inside them.
 * A year-stamped file is not a durable official URL. Callers keep the listing page.
 */
public final class CalendarPdf {

    private CalendarPdf() {}

    private static final Pattern PDF = Pattern.compile("\\.pdf(\\?|#|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_CALENDAR = Pattern.compile("calendar|programme|schedule", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALENDAR_BLOB = Pattern.compile(
            "calendar|programme|schedule|time[\\s-]?table", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPTY_DATE = Pattern.compile(
            "^(?:to be notified|yet to be notified|tbn|tbd|n/?a|nil|-+|–+|—+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKIP_NAME = Pattern.compile(
            "^(?:name of (?:the )?exam(?:ination)?|examination name|s\\.?\\s*no\\.?|sl\\.?\\s*no\\.?|date of notification|last date|remarks|total|tentative calendar)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_LINE = Pattern.compile(
            "name of exam|date of notification|last date|date of commencement|tentative date|tentative month",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JUNK_LINE = Pattern.compile(
            "^(?:page\\s+\\d+|www\\.|https?://|union public service|staff selection commission|annual calendar|tentative calendar|note:|remarks[:\\s])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_LINK = Pattern.compile(
            "\\b(?:link|url|website|apply here|notification pdf|click here)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_NOTIFY = Pattern.compile(
            "notif|date of advt|advertisement date", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_LAST = Pattern.compile(
            "last\\s*date|closing date|receipt of appl|completion of appl|closing of appl|apply\\s*(?:by|upto|until|on or before)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_EXAM = Pattern.compile(
            "(?:exam|commencement|tier).*(?:date|month)|date.*exam|month of exam|tentative month",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_NAME = Pattern.compile(
            "^name\\b|name of|examination name|exam name|name of the exam|post|vacancy title|^examination$|^exam$",
            Pattern.CASE_INSENSITIVE);
    private static final String MONTH_NAME =
            "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?";
    private static final Pattern MONTH_RANGE = Pattern.compile(
            "\\b(" + MONTH_NAME + ")\\.?\\s*[-–/to]+\\s*(?:" + MONTH_NAME + ")\\.?,?\\s*(20\\d{2})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTH_YEAR = Pattern.compile(
            "\\b(" + MONTH_NAME + ")\\.?,?\\s*(20\\d{2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_TOKEN = Pattern.compile(
            "\\b(\\d{1,2}(?:st|nd|rd|th)?[.\\-/]\\d{1,2}[.\\-/]20\\d{2}"
                    + "|\\d{1,2}(?:st|nd|rd|th)?[\\s,]+\\w{3,9}[\\s,]+20\\d{2}"
                    + "|\\w{3,9}\\s+\\d{1,2}(?:st|nd|rd|th)?[,\\s]+20\\d{2}"
                    + "|\\w{3,9}\\.?,?\\s*[-–/to]+\\s*\\w{3,9}\\.?,?\\s*20\\d{2}"
                    + "|\\w{3,9}\\.?,?\\s+20\\d{2})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1),
            Map.entry("january", 1),
            Map.entry("feb", 2),
            Map.entry("february", 2),
            Map.entry("mar", 3),
            Map.entry("march", 3),
            Map.entry("apr", 4),
            Map.entry("april", 4),
            Map.entry("may", 5),
            Map.entry("jun", 6),
            Map.entry("june", 6),
            Map.entry("jul", 7),
            Map.entry("july", 7),
            Map.entry("aug", 8),
            Map.entry("august", 8),
            Map.entry("sep", 9),
            Map.entry("sept", 9),
            Map.entry("september", 9),
            Map.entry("oct", 10),
            Map.entry("october", 10),
            Map.entry("nov", 11),
            Map.entry("november", 11),
            Map.entry("dec", 12),
            Map.entry("december", 12));

    /** PDF anchors a job-link score would drop. At most five, in page order. */
    public static List<Map<String, Object>> findLinks(String html, String pageUrl) {
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        boolean pageIsCalendar = pageUrl != null && PAGE_CALENDAR.matcher(pageUrl).find();
        List<Map<String, Object>> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element anchor : document.select("a[href]")) {
            if (links.size() == 5) {
                break;
            }
            String hrefRaw = anchor.attr("href").trim();
            if (JobLinkQuality.skipHref(hrefRaw)) {
                continue;
            }
            String href = anchor.absUrl("href");
            if (href.isEmpty()) {
                href = HtmlLinks.absoluteUrl(pageUrl, hrefRaw);
            }
            href = HtmlLinks.preferHttps(pageUrl, href);
            if (href == null || !href.regionMatches(true, 0, "https://", 0, 8) || !seen.add(href) || !PDF.matcher(href).find()) {
                continue;
            }
            String title = anchor.text().replaceAll("\\s+", " ").trim();
            if (title.isEmpty()) {
                title = anchor.attr("title").replaceAll("\\s+", " ").trim();
            }
            if (!pageIsCalendar && !CALENDAR_BLOB.matcher(title + " " + href).find()) {
                continue;
            }
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("title", title.length() > 240 ? title.substring(0, 240) : title);
            link.put("href", href);
            link.put("isPdf", true);
            links.add(link);
        }
        return links;
    }

    public static boolean isCalendarPdf(String pageUrl, String title, String href) {
        if (href == null || !PDF.matcher(href).find()) {
            return false;
        }
        if (pageUrl != null && PAGE_CALENDAR.matcher(pageUrl).find()) {
            return true;
        }
        return CALENDAR_BLOB.matcher((title == null ? "" : title) + " " + href).find();
    }

    /**
     * HTML calendar tables. A cell link stays the row URL. Month text such as {@code May 2026}
     * becomes {@code 2026-05}. Callers decide which of those values are durable job fields.
     */
    public static List<Map<String, Object>> parseHtml(String html, String pageUrl) {
        Document document = Jsoup.parse(html == null ? "" : html, pageUrl == null ? "" : pageUrl);
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element table : document.select("table")) {
            List<Element> tableRows = table.select("tr");
            if (tableRows.isEmpty()) {
                continue;
            }
            Element headerRow = table.selectFirst("thead tr");
            if (headerRow == null) {
                headerRow = tableRows.get(0);
            }
            List<String> headers = new ArrayList<>();
            int mapped = 0;
            for (Element cell : headerRow.select("th, td")) {
                String role = classifyHeader(cell.text());
                headers.add(role);
                if (role != null) {
                    mapped++;
                }
            }
            for (int index = 0; index < tableRows.size(); index++) {
                Element rowElement = tableRows.get(index);
                List<Element> cells = rowElement.select("th, td");
                if (cells.size() < 2) {
                    continue;
                }
                if (!rowElement.select("th").isEmpty() && rowElement.select("td").isEmpty()) {
                    continue;
                }
                int headerish = 0;
                for (Element cell : cells) {
                    if (classifyHeader(cell.text()) != null) {
                        headerish++;
                    }
                }
                if (headerish >= 2 && (index == 0 || mapped >= 2)) {
                    continue;
                }
                Map<String, Object> row = htmlRow(cells, headers, pageUrl);
                if (row == null) {
                    continue;
                }
                String key = row.get("name") + "|" + text(row.get("notificationDate")) + "|" + text(row.get("examDate"));
                if (seen.add(key)) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** One row per dated line. Dates fill notification, last date, then exam. */
    public static List<Map<String, Object>> parseText(String text, String pageUrl) {
        String page = pageUrl == null || pageUrl.isBlank() ? null : pageUrl;
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String pendingName = "";
        for (String rawLine : (text == null ? "" : text).replace("\r", "").split("\n", -1)) {
            String line = rawLine.replaceAll("\\s+", " ").trim();
            if (line.isEmpty()) {
                continue;
            }
            if (HEADER_LINE.matcher(line).find() || JUNK_LINE.matcher(line).find()) {
                pendingName = "";
                continue;
            }
            List<String> tokens = dateTokens(line);
            if (tokens.isEmpty()) {
                if (line.length() >= 6 && !SKIP_NAME.matcher(line).matches()) {
                    pendingName = line;
                }
                continue;
            }
            String nameFromLine = DATE_TOKEN.matcher(line).replaceAll(" ").replace("|", " ").replaceAll("\\s+", " ").trim();
            String name = nameFromLine.isEmpty() ? pendingName : nameFromLine;
            pendingName = "";
            Map<String, Object> row = normalize(name, tokens, page);
            if (row == null) {
                continue;
            }
            String key = row.get("name") + "|" + text(row.get("notificationDate")) + "|" + text(row.get("examDate"));
            if (seen.add(key)) {
                rows.add(row);
            }
        }
        return rows;
    }

    static String parseLooseDate(String text) {
        if (text == null) {
            return null;
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.isEmpty() || EMPTY_DATE.matcher(compact).matches()) {
            return null;
        }
        String iso = NoticeDates.parseDateFromText(compact);
        if (iso != null) {
            return iso;
        }
        Matcher range = MONTH_RANGE.matcher(compact);
        if (range.find()) {
            return yearMonth(range.group(2), monthIndex(range.group(1)));
        }
        Matcher monthYear = MONTH_YEAR.matcher(compact);
        if (monthYear.find()) {
            return yearMonth(monthYear.group(2), monthIndex(monthYear.group(1)));
        }
        return null;
    }

    private static String classifyHeader(String text) {
        String header = text == null ? "" : text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (header.isEmpty()) {
            return null;
        }
        if (HEADER_LINK.matcher(header).find() && !header.contains("date")) {
            return "officialUrl";
        }
        if (HEADER_NOTIFY.matcher(header).find()) {
            return "notificationDate";
        }
        if (HEADER_LAST.matcher(header).find()) {
            return "lastDate";
        }
        if (HEADER_EXAM.matcher(header).find()) {
            return "examDate";
        }
        if (HEADER_NAME.matcher(header).find()) {
            return "name";
        }
        return null;
    }

    private static Map<String, Object> htmlRow(List<Element> cells, List<String> headers, String pageUrl) {
        String name = null;
        String notification = null;
        String last = null;
        String exam = null;
        String official = null;
        List<String> leftoverText = new ArrayList<>();
        List<String> leftoverDates = new ArrayList<>();
        for (int index = 0; index < cells.size(); index++) {
            Element cell = cells.get(index);
            String cellText = cell.text().replaceAll("\\s+", " ").trim();
            String href = cellHref(cell, pageUrl);
            String role = index < headers.size() ? headers.get(index) : null;
            if (href != null && (official == null || href.matches("(?i).*(notif|apply|advert|\\.pdf).*"))) {
                official = href;
            }
            if ("officialUrl".equals(role) && href != null) {
                official = href;
                continue;
            }
            if ("name".equals(role)) {
                name = cellText;
                continue;
            }
            if ("notificationDate".equals(role)) {
                notification = cellText;
                continue;
            }
            if ("lastDate".equals(role)) {
                last = cellText;
                continue;
            }
            if ("examDate".equals(role)) {
                exam = cellText;
                continue;
            }
            if (!cellText.isEmpty()) {
                leftoverText.add(cellText);
            }
            leftoverDates.addAll(dateTokens(cellText));
        }
        if (name == null || name.isBlank()) {
            boolean numbered = !leftoverText.isEmpty() && leftoverText.get(0).matches("\\d{1,3}\\.?");
            for (int index = 0; index < leftoverText.size(); index++) {
                if (numbered && index == 0) {
                    continue;
                }
                String candidate = leftoverText.get(index);
                if (parseLooseDate(candidate) != null && candidate.length() < 24) {
                    continue;
                }
                if (candidate.length() >= 6 && !SKIP_NAME.matcher(candidate).matches()) {
                    name = candidate;
                    break;
                }
            }
        }
        String[] slots = {notification, last, exam};
        int token = 0;
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] != null && !slots[slot].isBlank()) {
                continue;
            }
            while (token < leftoverDates.size() && parseLooseDate(leftoverDates.get(token)) == null) {
                token++;
            }
            if (token < leftoverDates.size()) {
                slots[slot] = leftoverDates.get(token);
                token++;
            }
        }
        return namedRow(name, slots[0], slots[1], slots[2], official);
    }

    private static String cellHref(Element cell, String pageUrl) {
        Element anchor = cell.selectFirst("a[href]");
        if (anchor == null && "a".equals(cell.tagName())) {
            anchor = cell;
        }
        if (anchor == null) {
            return null;
        }
        String raw = anchor.attr("href").trim();
        if (JobLinkQuality.skipHref(raw)) {
            return null;
        }
        String href = anchor.absUrl("href");
        if (href.isEmpty()) {
            href = HtmlLinks.absoluteUrl(pageUrl, raw);
        }
        href = HtmlLinks.preferHttps(pageUrl, href);
        if (href == null || !href.regionMatches(true, 0, "https://", 0, 8)) {
            return null;
        }
        return href;
    }

    private static Map<String, Object> namedRow(
            String name, String notification, String last, String exam, String official) {
        String cleaned = name == null ? "" : name.replaceAll("\\s+", " ").replaceAll("^[\\d.)\\s-]+", "").trim();
        if (cleaned.length() < 6 || SKIP_NAME.matcher(cleaned).matches()) {
            return null;
        }
        if (cleaned.matches("(?i)^https?://.*") && cleaned.length() < 16) {
            return null;
        }
        if (cleaned.length() > 240) {
            cleaned = cleaned.substring(0, 240);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", cleaned);
        row.put("notificationDate", parseLooseDate(notification));
        row.put("lastDate", parseLooseDate(last));
        row.put("examDate", parseLooseDate(exam));
        row.put("officialUrl", official);
        return row;
    }

    private static Map<String, Object> normalize(String name, List<String> tokens, String pageUrl) {
        String cleaned = name == null ? "" : name.replaceAll("\\s+", " ").replaceAll("^[\\d.)\\s-]+", "").trim();
        if (cleaned.length() < 6 || SKIP_NAME.matcher(cleaned).matches()) {
            return null;
        }
        if (cleaned.matches("(?i)^https?://.*") && cleaned.length() < 16) {
            return null;
        }
        if (cleaned.length() > 240) {
            cleaned = cleaned.substring(0, 240);
        }
        String[] slots = new String[3];
        int token = 0;
        for (int slot = 0; slot < slots.length; slot++) {
            while (token < tokens.size() && parseLooseDate(tokens.get(token)) == null) {
                token++;
            }
            if (token < tokens.size()) {
                slots[slot] = parseLooseDate(tokens.get(token));
                token++;
            }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", cleaned);
        row.put("notificationDate", slots[0]);
        row.put("lastDate", slots[1]);
        row.put("examDate", slots[2]);
        row.put("officialUrl", pageUrl);
        return row;
    }

    private static List<String> dateTokens(String line) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = DATE_TOKEN.matcher(line);
        while (matcher.find()) {
            tokens.add(matcher.group(1));
        }
        return tokens;
    }

    private static Integer monthIndex(String token) {
        if (token == null) {
            return null;
        }
        String key = token.toLowerCase(Locale.ROOT).replace(".", "");
        Integer month = MONTHS.get(key);
        if (month != null) {
            return month;
        }
        return key.length() >= 3 ? MONTHS.get(key.substring(0, 3)) : null;
    }

    private static String yearMonth(String year, Integer month) {
        if (year == null || month == null) {
            return null;
        }
        return year + "-" + (month < 10 ? "0" : "") + month;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
