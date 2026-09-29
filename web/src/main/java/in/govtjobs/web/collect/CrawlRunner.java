package in.govtjobs.web.collect;

import in.govtjobs.web.ops.OfficialUrlPolicy;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CrawlRunner {

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ISO_DATE = Pattern.compile("\\b(20\\d{2})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private final CrawlDeskStore store;
    private final CrawlJobLogger jobs;
    private final OfficialUrlPolicy urls;
    private final PageFetcher fetcher;
    private final ExecutorService workers = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "collect-crawl");
        thread.setDaemon(true);
        return thread;
    });

    public CrawlRunner(CrawlDeskStore store, CrawlJobLogger jobs, OfficialUrlPolicy urls, PageFetcher fetcher) {
        this.store = store;
        this.jobs = jobs;
        this.urls = urls;
        this.fetcher = fetcher;
    }

    public String start(String startedBy) {
        List<Map<String, Object>> links = store.priorityLinks();
        String runId = store.openRun(startedBy, links.size());
        jobs.info(runId, "", "start", "Crawl queued by " + (startedBy == null || startedBy.isBlank() ? "ops" : startedBy) + " for " + links.size() + " priority links");
        workers.execute(() -> execute(runId, links));
        return runId;
    }

    void execute(String runId, List<Map<String, Object>> links) {
        List<String> keywords = store.keywordPhrases();
        jobs.info(runId, "", "keywords", "Priority keywords: " + String.join(", ", keywords));
        boolean any = false;
        for (Map<String, Object> link : links) {
            any = true;
            crawlOne(runId, String.valueOf(link.get("label")), String.valueOf(link.get("url")), keywords);
        }
        store.finishRun(runId, any ? "finished" : "finished");
        jobs.info(runId, "", "finish", "Crawl finished. Notices stay hidden until an operator approves them.");
    }

    private void crawlOne(String runId, String label, String rawUrl, List<String> keywords) {
        long started = System.currentTimeMillis();
        String sourceId = null;
        try {
            URI uri = urls.requireAllowed(rawUrl);
            sourceId = store.openSource(runId, label, uri.toString());
            jobs.info(runId, label, "fetch", "Fetching " + uri);
            PageFetcher.PageFetch page = fetcher.fetch(uri);
            if (page.status() < 200 || page.status() >= 300) {
                store.finishSource(sourceId, "failed", 0, 0, 0, "HTTP " + page.status(), elapsed(started));
                jobs.error(runId, label, "fetch", "HTTP " + page.status());
                return;
            }
            String title = titleOf(page.body(), label);
            String plain = TAG.matcher(page.body()).replaceAll(" ");
            String lower = plain.toLowerCase(Locale.ROOT);
            boolean matched = keywords.stream().anyMatch(k -> lower.contains(k.toLowerCase(Locale.ROOT)));
            LocalDate lastDate = firstDate(plain);
            String excerpt = excerpt(plain);
            if (!matched) {
                store.saveNotice(runId, sourceId, title, label, uri.toString(), lastDate, "", false, excerpt, "dropped", "No priority keyword");
                store.finishSource(sourceId, "ok", 0, 0, 1, "", elapsed(started));
                jobs.info(runId, label, "classify", "Dropped. No priority keyword in the page.");
                return;
            }
            boolean dated = lastDate != null;
            String disposition = dated ? "kept" : "review";
            String reason = dated ? "" : "No last date on the notice";
            boolean hasExam = lower.contains("examination") || lower.contains("written test");
            String selection = hasExam ? "Written, as read from the notice" : "Read the notice";
            store.saveNotice(runId, sourceId, title, label, uri.toString(), lastDate, selection, hasExam, excerpt, disposition, reason);
            store.finishSource(sourceId, "ok", dated ? 1 : 0, dated ? 0 : 1, 0, "", elapsed(started));
            jobs.info(runId, label, "classify", disposition + " \"" + title + "\"" + (reason.isBlank() ? "" : " (" + reason + ")"));
        } catch (RuntimeException | java.io.IOException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (sourceId == null) {
                sourceId = store.openSource(runId, label, rawUrl);
            }
            store.finishSource(sourceId, "failed", 0, 0, 0, message, elapsed(started));
            jobs.error(runId, label, "fetch", message);
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    private static long elapsed(long started) {
        return Math.max(0, System.currentTimeMillis() - started);
    }

    static String titleOf(String html, String fallback) {
        Matcher matcher = TITLE.matcher(html == null ? "" : html);
        if (!matcher.find()) {
            return fallback;
        }
        String title = matcher.group(1).replaceAll("\\s+", " ").trim();
        if (title.length() > 180) {
            title = title.substring(0, 180);
        }
        return title.isBlank() ? fallback : title;
    }

    static LocalDate firstDate(String text) {
        Matcher matcher = ISO_DATE.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static String excerpt(String plain) {
        String collapsed = plain.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 320 ? collapsed : collapsed.substring(0, 320);
    }
}
