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

/**
 * One source fetch. A page with no kept links and no errors is an empty run. Notices kept
 * alongside an error are a partial run. A blocked page, a failed NCS browser read, and a
 * Chandigarh RRB host are failures and produce no vacancies.
 */
public final class SourceCollect {

    private SourceCollect() {}

    public record Result(SourceRunKind kind, String message, List<Map<String, Object>> records, List<String> errors) {
        public boolean ok() {
            return kind != null && kind.producedNotices();
        }

        static Result failed(String message) {
            return of(message, List.of(), List.of(message));
        }

        static Result of(String message, List<Map<String, Object>> records, List<String> errors) {
            List<Map<String, Object>> kept = records == null || records.isEmpty() ? List.of() : List.copyOf(records);
            List<String> problems = errors == null || errors.isEmpty() ? List.of() : List.copyOf(errors);
            return new Result(SourceRunKind.from(kept, problems), message, kept, problems);
        }
    }

    public static Result collect(SourceSpec source, SiteClient client) {
        return collect(source, client, Instant.now().toString());
    }

    public static Result collect(SourceSpec source, SiteClient client, String collectedAt) {
        return collect(source, client, collectedAt, PdfBudget.perSource(10));
    }

    /** Per-source cap is ten. A shared run counter, when present, is the daily PDF_MAX_PER_RUN budget. */
    static Result collect(SourceSpec source, SiteClient client, String collectedAt, PdfBudget pdfBudget) {
        if (source.defence() || source.manual()) {
            return Result.failed(SourceRules.CAPTCHA);
        }
        if (source.ncs()) {
            return collectNcs(source, client, collectedAt);
        }
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> records = new ArrayList<>();
        PdfBudget budget = pdfBudget == null ? PdfBudget.perSource(10) : pdfBudget;
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
                    absorb(source, client, body, collectedAt, records, errors, budget, highlightKeys);
                }
                return finishSource(source, records, errors);
            } catch (IOException ex) {
                errors.add("browser unavailable: " + blockedMessage(ex));
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        int[] hopBudget = {4};
        int[] browserBudget = {2};
        Set<String> highlightKeys = new LinkedHashSet<>();
        for (String listUrl : source.listUrls()) {
            pull(source, client, listUrl, collectedAt, records, errors, budget, seen, hopBudget, browserBudget, highlightKeys, true);
        }
        return finishSource(source, records, errors);
    }

    /** One list URL, then a browser render or a same-site listing hop when that page kept nothing. */
    private static void pull(
            SourceSpec source,
            SiteClient client,
            String listUrl,
            String collectedAt,
            List<Map<String, Object>> records,
            List<String> errors,
            PdfBudget pdfBudget,
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
            PdfBudget pdfBudget,
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
        if (source.sbi()) {
            List<Map<String, Object>> openings = SbiNotices.openings(body.html(), pageUrl);
            if (!openings.isEmpty()) {
                for (Map<String, Object> opening : openings) {
                    String title = String.valueOf(opening.get("title"));
                    opening.put("organization", "State Bank of India");
                    opening.put("hasExam", SbiNotices.hasExam(title));
                    opening.put("selectionProcess", SbiNotices.selection(title));
                    stageNotice(source, client, opening, pageUrl, collectedAt, records, errors, pdfBudget);
                }
                return;
            }
        }
        if (source.ibps() || source.upsc() || source.ssc() || source.psc() || source.calendar()) {
            records.addAll(calendarRecords(source, body.html(), pageUrl, collectedAt));
        }
        Set<String> calendarPdfs = new LinkedHashSet<>();
        if (readsCalendarPdfs(source)) {
            records.addAll(calendarPdfRecords(
                    source, client, body.html(), pageUrl, collectedAt, errors, pdfBudget, calendarPdfs));
        }
        List<Map<String, Object>> links = new ArrayList<>(HtmlLinks.extractLinks(body.html(), pageUrl));
        links.addAll(ListingPages.listingDocuments(body.html(), pageUrl));
        links.addAll(ListingPages.advtRowsWithoutLinks(body.html(), pageUrl));
        links.addAll(ListingPages.embeddedAdvertPdfs(body.html(), pageUrl));
        for (Map<String, Object> link : links) {
            String href = String.valueOf(link.get("href"));
            String title = String.valueOf(link.get("title"));
            if (calendarPdfs.contains(href) || SourceRules.isYearStampedCalendarPdf(href)) {
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
            if (source.psc() && !SourceRules.isPscNoticeLink(title, href, Boolean.TRUE.equals(link.get("isPdf")))) {
                continue;
            }
            if ("apprenticeship_india".equals(source.sourceId())
                    && !((title + " " + href).matches(
                            "(?i).*(vacancy|walk-?in|recruitment of|engagement of|applications are invited|notification for the post).*"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(link);
            if (source.ibps()) {
                item.put("organization", "Institute of Banking Personnel Selection");
                item.put("hasExam", true);
                item.put("selectionProcess", "cbt");
                item.put("extraText", "computer based test IBPS CRP");
            } else if (source.upsc()) {
                item.put("organization", "Union Public Service Commission");
                item.put("hasExam", true);
                item.put("selectionProcess", upscSelection(title));
                item.put("extraText", "written examination multi stage");
            } else if (source.ssc()) {
                item.put("organization", "Staff Selection Commission");
                item.put("hasExam", true);
                item.put("selectionProcess", sscSelection(title));
                item.put("extraText", "computer based test SSC");
            } else if (source.psc()) {
                item.put("organization", source.name());
                item.put("hasExam", true);
                item.put("selectionProcess", "written_multi_stage");
                item.put("extraText", "written examination multi stage state PSC");
            } else if (source.board() && "apprenticeship".equals(source.opportunityType())) {
                item.put("extraText", "apprenticeship");
            }
            stageNotice(source, client, item, pageUrl, collectedAt, records, errors, pdfBudget);
        }
    }

    private static void stageNotice(
            SourceSpec source,
            SiteClient client,
            Map<String, Object> item,
            String pageUrl,
            String collectedAt,
            List<Map<String, Object>> records,
            List<String> errors,
            PdfBudget pdfBudget) {
        String href = String.valueOf(item.get("href"));
        String title = String.valueOf(item.get("title"));
        item.put("sourceUrl", pageUrl);
        if (noticePdf(client, item, href) && !enrichPdf(client, item, href, title, errors, pdfBudget)) {
            return;
        }
        Object marked = item.get("collectorVersion");
        String version = marked == null || String.valueOf(marked).isBlank() ? "scrape-v1" : String.valueOf(marked);
        records.add(StagingRecords.toStagingRecord(item, source, collectedAt, version));
    }

    /** Extension, an existing PDF flag, or a document href whose content type is PDF. */
    private static boolean noticePdf(SiteClient client, Map<String, Object> item, String href) {
        if (Boolean.TRUE.equals(item.get("isPdf"))) {
            return true;
        }
        if (!JobLinkQuality.looksLikeDocumentHref(href)) {
            return false;
        }
        try {
            if (client.documentPdf(href)) {
                item.put("isPdf", true);
                return true;
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    /**
     * A notice PDF is kept only when its text is recruitment evidence. A timeout, a failed
     * fetch, a file that is not a PDF, text that is not a notice, or the ten-PDF cap drops the anchor.
     */
    private static boolean enrichPdf(
            SiteClient client,
            Map<String, Object> item,
            String href,
            String title,
            List<String> errors,
            PdfBudget pdfBudget) {
        if (!pdfBudget.tryAcquire()) {
            errors.add("pdf skipped: PDF cap reached");
            return false;
        }
        byte[] bytes;
        try {
            bytes = client.bytes(href);
        } catch (IOException ex) {
            pdfBudget.release();
            errors.add("pdf skipped: " + blockedMessage(ex));
            return false;
        }
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P') {
            errors.add("pdf skipped: pdf_parse_failed");
            return false;
        }
        String text;
        try {
            text = PdfDocuments.text(bytes);
        } catch (IOException ex) {
            errors.add("pdf skipped: pdf_parse_failed");
            return false;
        }
        client.rememberPdf(bytes, text);
        Map<String, Object> fields = PdfDocuments.parseFields(text);
        if (!Boolean.TRUE.equals(fields.get("isJobNotice"))) {
            errors.add("pdf skipped: pdf_not_recruitment");
            return false;
        }
        if (fields.get("lastDate") != null) {
            item.put("lastDate", fields.get("lastDate"));
        }
        if (fields.get("vacancies") != null) {
            item.put("vacancies", fields.get("vacancies"));
        }
        Object parsedTitle = fields.get("title");
        if (parsedTitle != null && title.length() <= 12) {
            item.put("title", parsedTitle);
        }
        Object excerpt = fields.get("excerpt");
        if (excerpt != null && !String.valueOf(excerpt).isBlank()) {
            item.put("summary", excerpt);
        }
        item.put("extraText", slice(text, 4000));
        item.put("pdfText", slice(text, 8000));
        item.put("pdfHash", PdfDocuments.sha256(bytes));
        item.put("collectorVersion", "pdf-v1");
        return true;
    }

    private static boolean readsCalendarPdfs(SourceSpec source) {
        return source.ibps() || source.upsc() || source.ssc() || source.calendar();
    }

    /** At most two calendar PDFs. Rows point at the listing page, not the file. */
    private static List<Map<String, Object>> calendarPdfRecords(
            SourceSpec source,
            SiteClient client,
            String html,
            String pageUrl,
            String collectedAt,
            List<String> errors,
            PdfBudget pdfBudget,
            Set<String> attempted) {
        List<Map<String, Object>> records = new ArrayList<>();
        int downloaded = 0;
        for (Map<String, Object> link : CalendarPdf.findLinks(html, pageUrl)) {
            if (downloaded == 2) {
                break;
            }
            String href = String.valueOf(link.get("href"));
            attempted.add(href);
            if (!pdfBudget.tryAcquire()) {
                errors.add("pdf skipped: PDF cap reached");
                break;
            }
            downloaded++;
            byte[] bytes;
            try {
                bytes = client.bytes(href);
            } catch (IOException ex) {
                pdfBudget.release();
                errors.add("calendar PDF parse failed: " + blockedMessage(ex));
                continue;
            }
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P') {
                errors.add("calendar PDF parse failed: pdf_parse_failed");
                continue;
            }
            String text;
            try {
                text = PdfDocuments.text(bytes);
            } catch (IOException ex) {
                errors.add("calendar PDF parse failed: pdf_parse_failed");
                continue;
            }
            client.rememberPdf(bytes, text);
            for (Map<String, Object> row : CalendarPdf.parseText(text, pageUrl)) {
                records.add(calendarPdfRecord(source, row, pageUrl, collectedAt));
            }
        }
        return records;
    }

    private static Map<String, Object> calendarPdfRecord(
            SourceSpec source, Map<String, Object> row, String pageUrl, String collectedAt) {
        return calendarStaging(source, row, pageUrl, pageUrl, collectedAt, true);
    }

    private static Map<String, Object> calendarHtmlRecord(
            SourceSpec source, Map<String, Object> row, String pageUrl, String collectedAt) {
        String official = pageUrl;
        Object cell = row.get("officialUrl");
        if (cell != null) {
            String href = String.valueOf(cell);
            if (href.regionMatches(true, 0, "https://", 0, 8) && !SourceRules.isYearStampedCalendarPdf(href)) {
                official = href;
            }
        }
        return calendarStaging(source, row, official, pageUrl, collectedAt, false);
    }

    /**
     * PDF rows keep a month notification such as {@code 2026-09}. HTML rows keep notification and
     * last date only as a full day. The official URL of a PDF row is the listing page.
     */
    private static Map<String, Object> calendarStaging(
            SourceSpec source,
            Map<String, Object> row,
            String officialUrl,
            String pageUrl,
            String collectedAt,
            boolean keepMonthNotification) {
        String name = String.valueOf(row.get("name"));
        Object notification = row.get("notificationDate");
        if (notification != null && !keepMonthNotification && !fullIso(notification)) {
            notification = null;
        }
        Object exam = row.get("examDate");
        StringBuilder summary = new StringBuilder(name);
        if (notification != null) {
            summary.append(". Notification: ").append(notification);
        }
        if (fullIso(row.get("lastDate"))) {
            summary.append(". Last date: ").append(row.get("lastDate"));
        }
        if (exam != null) {
            summary.append(". Exam: ").append(exam);
        }
        summary.append(". Eligibility must be verified on the official site.");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", name);
        item.put("href", officialUrl);
        item.put("officialUrl", officialUrl);
        item.put("sourceUrl", pageUrl);
        item.put("organization", source.ibps() ? "Institute of Banking Personnel Selection" : source.name());
        item.put("hasExam", true);
        item.put("selectionProcess", calendarSelection(source, name));
        item.put("extraText", calendarExtra(source, name));
        item.put("summary", summary.toString());
        if (notification != null) {
            item.put("notificationDate", notification);
        }
        if (fullIso(row.get("lastDate"))) {
            item.put("lastDate", row.get("lastDate"));
        }
        if (exam != null) {
            item.put("examDate", exam);
        }
        return StagingRecords.toStagingRecord(item, source, collectedAt, "calendar-v1");
    }

    private static String calendarSelection(SourceSpec source, String name) {
        if (source.upsc()) {
            return upscSelection(name);
        }
        if (source.ssc()) {
            return sscSelection(name);
        }
        if (source.psc()) {
            return "written_multi_stage";
        }
        return "cbt";
    }

    private static String calendarExtra(SourceSpec source, String name) {
        if (source.ibps()) {
            return "computer based test IBPS CRP";
        }
        if (source.ssc()) {
            return "computer based test SSC";
        }
        if (source.psc()) {
            return "written examination multi stage state PSC calendar";
        }
        if (source.upsc()) {
            return "cbt".equals(upscSelection(name))
                    ? "computer based test"
                    : "written examination multi stage UPSC";
        }
        return "exam calendar";
    }

    private static String upscSelection(String name) {
        return name != null && name.matches("(?i).*computer\\s*[- ]?based.*|.*\\bcbt\\b.*")
                ? "cbt"
                : "written_multi_stage";
    }

    private static String sscSelection(String name) {
        if (name != null
                && name.matches("(?i).*written.*|.*\\btier\\b.*|.*prelim.*|.*mains.*")
                && !name.matches("(?i).*\\bcbt\\b.*|.*computer\\s*[- ]?based.*")) {
            return "written_multi_stage";
        }
        return "cbt";
    }

    private static boolean fullIso(Object value) {
        return value != null && String.valueOf(value).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private static String slice(String text, int max) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max);
    }

    private static Result finishSource(SourceSpec source, List<Map<String, Object>> records, List<String> errors) {
        if ((source.psc() || source.board()) && records.isEmpty()) {
            errors.add(source.psc()
                    ? "listUrls did not parse a notice list — leave disabled or use ops paste-URL"
                    : "listUrls did not parse — use ops paste-URL");
        }
        return finish(records, errors);
    }

    private static Result finish(List<Map<String, Object>> records, List<String> errors) {
        List<Map<String, Object>> unique = StagingRecords.dedupeByUrl(records);
        if (!unique.isEmpty()) {
            return Result.of(null, unique, errors);
        }
        if (!errors.isEmpty()) {
            return Result.of(errors.get(0), List.of(), errors);
        }
        return Result.of(SourceRules.NO_KEEPABLE, List.of(), List.of());
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
                return Result.of("no current notices: " + emptyNote, List.of(), List.of());
            }
            if (errors.isEmpty() && visibleNote != null) {
                return Result.of("no keepable notices: " + visibleNote, List.of(), List.of(SourceRules.NCS_EMPTY));
            }
            List<String> all = new ArrayList<>(errors);
            all.add(SourceRules.NCS_EMPTY);
            return Result.of(SourceRules.NCS_EMPTY, List.of(), all);
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> link : kept) {
            records.add(StagingRecords.toStagingRecord(link, source, collectedAt, "playwright-v1"));
        }
        return Result.of(null, StagingRecords.dedupeByUrl(records), errors);
    }

    private static List<Map<String, Object>> calendarRecords(
            SourceSpec source, String html, String pageUrl, String collectedAt) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : CalendarPdf.parseHtml(html, pageUrl)) {
            records.add(calendarHtmlRecord(source, row, pageUrl, collectedAt));
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
            PdfBudget pdfBudget,
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
