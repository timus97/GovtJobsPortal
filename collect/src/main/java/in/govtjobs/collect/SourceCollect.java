package in.govtjobs.collect;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * One source fetch. A page with no kept links is a clean zero. A blocked page, a failed NCS
 * browser read, and a Chandigarh RRB host are errors and produce no vacancies.
 */
public final class SourceCollect {

    private SourceCollect() {}

    public record Result(boolean ok, String message, List<Map<String, Object>> records, List<String> errors) {
        static Result failed(String message) {
            return new Result(false, message, List.of(), List.of(message));
        }
    }

    public static Result collect(SourceSpec source, SiteClient client) {
        return collect(source, client, Instant.now().toString());
    }

    public static Result collect(SourceSpec source, SiteClient client, String collectedAt) {
        if (source.defence() || source.manual()) {
            return Result.failed(SourceRules.CAPTCHA);
        }
        if (source.ncs()) {
            return collectNcs(source, client, collectedAt);
        }
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> records = new ArrayList<>();
        int[] pdfBudget = {10};
        if (source.browser()) {
            List<String> urls = new ArrayList<>();
            for (String listUrl : source.listUrls()) {
                if (listUrl != null && listUrl.regionMatches(true, 0, "https://", 0, 8)) {
                    urls.add(listUrl);
                }
            }
            try {
                Set<String> highlightKeys = new LinkedHashSet<>();
                for (SiteClient.PageBody body : client.browser(urls)) {
                    absorb(source, client, body, collectedAt, records, errors, pdfBudget, highlightKeys);
                }
                return finish(records, errors);
            } catch (IOException ex) {
                errors.add("browser unavailable: " + blockedMessage(ex));
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        int[] hopBudget = {4};
        int[] browserBudget = {2};
        Set<String> highlightKeys = new LinkedHashSet<>();
        for (String listUrl : source.listUrls()) {
            pull(source, client, listUrl, collectedAt, records, errors, pdfBudget, seen, hopBudget, browserBudget, highlightKeys, true);
        }
        return finish(records, errors);
    }

    /** One list URL, then a browser render or a same-site listing hop when that page kept nothing. */
    private static void pull(
            SourceSpec source,
            SiteClient client,
            String listUrl,
            String collectedAt,
            List<Map<String, Object>> records,
            List<String> errors,
            int[] pdfBudget,
            Set<String> seen,
            int[] hopBudget,
            int[] browserBudget,
            Set<String> highlightKeys,
            boolean allowHop) {
        if (SourceRules.isYearStampedCalendarPdf(listUrl)) {
            errors.add(SourceRules.YEAR_STAMPED_PDF);
            return;
        }
        if (source.rrb() && SourceRules.isChandigarhZonalHost(listUrl)) {
            errors.add(SourceRules.CHANDIGARH);
            return;
        }
        if (listUrl == null || !listUrl.regionMatches(true, 0, "https://", 0, 8)) {
            errors.add("non-https link dropped: " + listUrl);
            return;
        }
        if (!seen.add(listUrl)) {
            return;
        }
        SiteClient.PageBody body;
        try {
            body = client.page(listUrl);
        } catch (IOException ex) {
            if (browserBudget[0] > 0 && browserMayOpen(ex) && openInBrowser(
                    source,
                    client,
                    listUrl,
                    collectedAt,
                    records,
                    errors,
                    pdfBudget,
                    browserBudget,
                    highlightKeys)) {
                return;
            }
            errors.add(blockedMessage(ex));
            return;
        }
        if (body == null || body.html() == null) {
            errors.add("empty page");
            return;
        }
        if (SourceRules.looksBlocked(body.html())) {
            if (browserBudget[0] > 0
                    && SourceRules.wafInterstitial(body.html())
                    && openInBrowser(
                            source,
                            client,
                            listUrl,
                            collectedAt,
                            records,
                            errors,
                            pdfBudget,
                            browserBudget,
                            highlightKeys)) {
                return;
            }
            errors.add(SourceRules.CAPTCHA);
            return;
        }
        int before = records.size();
        absorb(source, client, body, collectedAt, records, errors, pdfBudget, highlightKeys);
        if (records.size() > before || ListingPages.foreignHalPage(source, body.html())) {
            return;
        }
        String html = body.html();
        if (browserBudget[0] > 0 && ListingPages.needsBrowser(html)) {
            browserBudget[0]--;
            try {
                for (SiteClient.PageBody rendered : client.browser(List.of(listUrl))) {
                    if (rendered == null || rendered.html() == null || SourceRules.looksBlocked(rendered.html())) {
                        errors.add(SourceRules.CAPTCHA);
                        continue;
                    }
                    try {
                        JsoupPages.refuseBotManager(rendered.url());
                    } catch (IOException wall) {
                        errors.add(blockedMessage(wall));
                        continue;
                    }
                    absorb(source, client, rendered, collectedAt, records, errors, pdfBudget, highlightKeys);
                    if (rendered.html().length() > html.length()) {
                        html = rendered.html();
                    }
                }
            } catch (IOException ex) {
                errors.add("browser unavailable: " + blockedMessage(ex));
            }
            if (records.size() > before) {
                return;
            }
        }
        if (!allowHop || hopBudget[0] <= 0 || "employmentNews".equals(source.collector())) {
            return;
        }
        for (String hop : ListingPages.hops(html, body.url() == null ? listUrl : body.url())) {
            if (hopBudget[0] <= 0 || records.size() > before) {
                return;
            }
            hopBudget[0]--;
            pull(source, client, hop, collectedAt, records, errors, pdfBudget, seen, hopBudget, browserBudget, highlightKeys, false);
        }
    }

    private static void absorb(
            SourceSpec source,
            SiteClient client,
            SiteClient.PageBody body,
            String collectedAt,
            List<Map<String, Object>> records,
            List<String> errors,
            int[] pdfBudget,
            Set<String> highlightKeys) {
        if (body == null || body.html() == null) {
            errors.add("empty page");
            return;
        }
        if (SourceRules.looksBlocked(body.html())) {
            errors.add(SourceRules.CAPTCHA);
            return;
        }
        if (ListingPages.foreignHalPage(source, body.html())) {
            errors.add("page title belongs to a different organisation: " + ListingPages.titleOf(body.html()));
            return;
        }
        String pageUrl = body.url() == null || body.url().isBlank()
                ? source.listUrls().stream().findFirst().orElse("")
                : body.url();
        if ("employmentNews".equals(source.collector())) {
            for (Map<String, Object> item : ListingPages.highlights(body.html(), pageUrl)) {
                String key = String.valueOf(item.get("organization"))
                        + "|"
                        + item.get("title")
                        + "|"
                        + item.get("lastDate");
                if (highlightKeys.add(key.toLowerCase(Locale.ROOT))) {
                    records.add(StagingRecords.toStagingRecord(item, source, collectedAt, "highlights-v1"));
                }
            }
            return;
        }
        if (source.ibps() || source.calendar()) {
            records.addAll(calendarRecords(source, body.html(), pageUrl, collectedAt));
        }
        List<Map<String, Object>> links = new ArrayList<>(HtmlLinks.extractLinks(body.html(), pageUrl));
        links.addAll(ListingPages.listingDocuments(body.html(), pageUrl));
        links.addAll(ListingPages.advtRowsWithoutLinks(body.html(), pageUrl));
        links.addAll(ListingPages.embeddedAdvertPdfs(body.html(), pageUrl));
        for (Map<String, Object> link : links) {
            String href = String.valueOf(link.get("href"));
            String title = String.valueOf(link.get("title"));
            if (SourceRules.isYearStampedCalendarPdf(href)) {
                continue;
            }
            if (source.ibps() && !SourceRules.isIbpsNoticeLink(title, href)) {
                continue;
            }
            if (source.rrb() && !SourceRules.isRrbNoticeLink(title, href)) {
                continue;
            }
            if ((source.upsc() || source.ssc()) && !SourceRules.isBoardNoticeLink(title, href)) {
                continue;
            }
            if ("apprenticeship_india".equals(source.sourceId())
                    && !((title + " " + href).matches(
                            "(?i).*(vacancy|walk-?in|recruitment of|engagement of|applications are invited|notification for the post).*"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(link);
            item.put("sourceUrl", pageUrl);
            if (source.ibps()) {
                item.put("organization", "Institute of Banking Personnel Selection");
                item.put("hasExam", true);
                item.put("selectionProcess", "cbt");
                item.put("extraText", "computer based test IBPS CRP");
            } else if (source.upsc()) {
                item.put("organization", "Union Public Service Commission");
                item.put("hasExam", true);
                item.put("extraText", "written examination multi stage");
            } else if (source.ssc()) {
                item.put("organization", "Staff Selection Commission");
                item.put("hasExam", true);
                item.put("extraText", "computer based test SSC");
            }
            if (Boolean.TRUE.equals(link.get("isPdf")) && !enrichPdf(client, item, href, title, errors, pdfBudget)) {
                continue;
            }
            records.add(StagingRecords.toStagingRecord(item, source, collectedAt, "scrape-v1"));
        }
    }

    /** Pull a last date from a notice PDF. A brochure or an oversized file is dropped. */
    private static boolean enrichPdf(
            SiteClient client,
            Map<String, Object> item,
            String href,
            String title,
            List<String> errors,
            int[] pdfBudget) {
        if (pdfBudget[0] <= 0) {
            return true;
        }
        pdfBudget[0]--;
        try {
            byte[] bytes = client.bytes(href);
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P') {
                return true;
            }
            String text = PdfDocuments.text(bytes);
            if (JobLinkQuality.rejectedDocument(text)) {
                return false;
            }
            Map<String, Object> fields = PdfDocuments.parseFields(text);
            if (fields.get("lastDate") != null) {
                item.put("lastDate", fields.get("lastDate"));
            }
            if (fields.get("vacancies") != null) {
                item.put("vacancies", fields.get("vacancies"));
            }
            Object parsedTitle = fields.get("title");
            if (parsedTitle != null && String.valueOf(parsedTitle).length() > title.length()) {
                item.put("title", parsedTitle);
            }
            return true;
        } catch (IOException ex) {
            errors.add("pdf skipped: " + blockedMessage(ex));
            return true;
        }
    }

    private static Result finish(List<Map<String, Object>> records, List<String> errors) {
        List<Map<String, Object>> unique = StagingRecords.dedupeByUrl(records);
        if (!unique.isEmpty()) {
            return new Result(true, null, unique, List.copyOf(errors));
        }
        if (!errors.isEmpty()) {
            return new Result(false, errors.get(0), List.of(), List.copyOf(errors));
        }
        return new Result(true, SourceRules.NO_KEEPABLE, List.of(), List.of());
    }

    private static Result collectNcs(SourceSpec source, SiteClient client, String collectedAt) {
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();
        String emptyNote = null;
        String visibleNote = null;
        try {
            for (SiteClient.PageBody body : client.browser(source.listUrls())) {
                if (body == null || body.html() == null || SourceRules.looksBlocked(body.html())) {
                    errors.add(SourceRules.CAPTCHA);
                    continue;
                }
                try {
                    JsoupPages.refuseBotManager(body.url());
                } catch (IOException wall) {
                    errors.add(blockedMessage(wall));
                    continue;
                }
                String pageUrl = body.url() == null ? source.listUrls().stream().findFirst().orElse("") : body.url();
                links.addAll(HtmlLinks.extractLinks(body.html(), pageUrl));
                String note = emptyListingNote(body.html());
                if (note != null) {
                    emptyNote = note;
                }
                String visible = visibleListingNote(body.html());
                if (visible != null) {
                    visibleNote = visible;
                }
                // NCS stays on the rendered anchors. Highlight tables and listing hops are not used.
            }
        } catch (IOException ex) {
            errors.add(ex.getMessage() == null ? "playwright failed" : ex.getMessage());
        }
        List<Map<String, Object>> kept = JobLinkQuality.filterJobLinks(links);
        if (kept.isEmpty()) {
            if (errors.isEmpty() && emptyNote != null) {
                return new Result(true, "no current notices: " + emptyNote, List.of(), List.of());
            }
            if (errors.isEmpty() && visibleNote != null) {
                return new Result(false, "no keepable notices: " + visibleNote, List.of(), List.of(SourceRules.NCS_EMPTY));
            }
            List<String> all = new ArrayList<>(errors);
            all.add(SourceRules.NCS_EMPTY);
            return new Result(false, SourceRules.NCS_EMPTY, List.of(), List.copyOf(all));
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> link : kept) {
            records.add(StagingRecords.toStagingRecord(link, source, collectedAt, "playwright-v1"));
        }
        return new Result(true, null, StagingRecords.dedupeByUrl(records), List.copyOf(errors));
    }

    private static List<Map<String, Object>> calendarRecords(
            SourceSpec source, String html, String pageUrl, String collectedAt) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Element table : Jsoup.parse(html, pageUrl).select("table")) {
            List<Element> rows = table.select("tr");
            if (rows.size() < 2) {
                continue;
            }
            List<String> headers = cells(rows.get(0));
            int nameCol = column(headers, "name", "examination", "post");
            int examCol = examColumn(headers);
            int lastCol = column(headers, "last");
            if (nameCol < 0 && lastCol < 0) {
                continue;
            }
            for (int i = 1; i < rows.size(); i++) {
                List<String> values = cells(rows.get(i));
                if (values.size() < 2) {
                    continue;
                }
                String name = pick(values, nameCol, 0);
                if (name.length() < 6 || name.toLowerCase(Locale.ROOT).startsWith("name of")) {
                    continue;
                }
                String last = lastCol >= 0 ? pick(values, lastCol, -1) : "";
                String exam = examCol >= 0 ? pick(values, examCol, -1) : "";
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", name);
                item.put("href", pageUrl);
                item.put("officialUrl", pageUrl);
                item.put("sourceUrl", pageUrl);
                item.put("organization", source.ibps() ? "Institute of Banking Personnel Selection" : source.name());
                item.put("hasExam", true);
                item.put("selectionProcess", "cbt");
                item.put("examDate", exam.isBlank() ? null : exam);
                String isoLast = NoticeDates.parseDateFromText(last);
                if (isoLast != null) {
                    item.put("lastDate", isoLast);
                }
                item.put("summary", name + ". Eligibility must be verified on the official site.");
                records.add(StagingRecords.toStagingRecord(item, source, collectedAt, "calendar-v1"));
            }
        }
        if (records.isEmpty() && source.calendar() && !source.ibps()) {
            for (Map<String, Object> item : ListingPages.calendarProse(html, pageUrl)) {
                item.put("organization", source.name());
                item.put("hasExam", true);
                item.put("selectionProcess", "cbt");
                item.put("sourceUrl", pageUrl);
                records.add(StagingRecords.toStagingRecord(item, source, collectedAt, "calendar-v1"));
            }
        }
        return records;
    }

    private static List<String> cells(Element row) {
        List<String> out = new ArrayList<>();
        for (Element cell : row.select("th, td")) {
            out.add(cell.text().replaceAll("\\s+", " ").trim());
        }
        return out;
    }

    private static int examColumn(List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).toLowerCase(Locale.ROOT);
            if (header.contains("name")) {
                continue;
            }
            if (header.contains("exam")) {
                return i;
            }
        }
        return -1;
    }

    private static int column(List<String> headers, String... needles) {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (header.contains(needle)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String pick(List<String> values, int index, int fallback) {
        if (index >= 0 && index < values.size()) {
            return values.get(index);
        }
        if (fallback >= 0 && fallback < values.size()) {
            return values.get(fallback);
        }
        return "";
    }

    /**
     * A bot user-agent is refused, or a same-site script sets a cookie before the HTML arrives.
     * A foreign redirect and a CAPTCHA are not opened in the browser.
     */
    private static boolean browserMayOpen(IOException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("javascript redirect")
                || message.contains("http 403")
                || message.contains("pkix")
                || message.contains("certif")
                || message.contains("handshake")
                || message.contains("ssl");
    }

    /** @return true when the browser returned a page, so the HTTP error is not also recorded. */
    private static boolean openInBrowser(
            SourceSpec source,
            SiteClient client,
            String listUrl,
            String collectedAt,
            List<Map<String, Object>> records,
            List<String> errors,
            int[] pdfBudget,
            int[] browserBudget,
            Set<String> highlightKeys) {
        browserBudget[0]--;
        try {
            boolean opened = false;
            for (SiteClient.PageBody rendered : client.browser(List.of(listUrl))) {
                if (rendered == null || rendered.html() == null) {
                    continue;
                }
                try {
                    JsoupPages.refuseBotManager(rendered.url());
                } catch (IOException wall) {
                    errors.add(blockedMessage(wall));
                    return true;
                }
                if (SourceRules.looksBlocked(rendered.html())) {
                    errors.add(SourceRules.CAPTCHA);
                    return true;
                }
                opened = true;
                absorb(source, client, rendered, collectedAt, records, errors, pdfBudget, highlightKeys);
            }
            return opened;
        } catch (IOException ex) {
            errors.add("browser unavailable: " + blockedMessage(ex));
            return false;
        }
    }

    /** NCS government-job list that rendered and named zero posts. */
    private static String emptyListingNote(String html) {
        String text = Jsoup.parse(html == null ? "" : html).text().replaceAll("\\s+", " ").trim();
        String lower = text.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("0 job posts and 0 vacancies");
        String quote = "0 job posts and 0 vacancies";
        if (at < 0) {
            at = lower.indexOf("0 job posts");
            quote = "0 job posts";
        }
        if (at < 0) {
            return null;
        }
        String shown = text.substring(at, Math.min(text.length(), at + quote.length()));
        String title = ListingPages.titleOf(html);
        return title.isBlank() ? shown : title + " — " + shown;
    }

    /** Title plus a short visible quote when the rendered list is not the empty-jobs sentence. */
    private static String visibleListingNote(String html) {
        String text = Jsoup.parse(html == null ? "" : html).text().replaceAll("\\s+", " ").trim();
        if (text.length() < 80) {
            return null;
        }
        String quote = text.length() > 180 ? text.substring(0, 180).trim() : text;
        String title = ListingPages.titleOf(html);
        return title.isBlank() ? quote : title + " — " + quote;
    }

    private static String blockedMessage(IOException ex) {
        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        if (message.matches("(?i).*(HTTP 403|HTTP 429|captcha|blocked).*")) {
            return "blocked: " + message;
        }
        return message;
    }
}
