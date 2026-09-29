package in.govtjobs.web.collect;

import in.govtjobs.domain.job.JobSchema;
import in.govtjobs.web.ops.OfficialUrlPolicy;
import in.govtjobs.web.store.StoreException;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrawlDeskStore {

    private final JdbcTemplate jdbc;
    private final OfficialUrlPolicy urls;

    public CrawlDeskStore(JdbcTemplate jdbc, OfficialUrlPolicy urls) {
        this.jdbc = jdbc;
        this.urls = urls;
    }

    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", count("SELECT COUNT(*) FROM collect.runs WHERE status = 'running'"));
        out.put("waiting", count("SELECT COUNT(*) FROM collect.notices WHERE review_status = 'waiting'"));
        out.put(
                "approvedToday",
                count(
                        """
                        SELECT COUNT(*) FROM collect.notices
                        WHERE review_status = 'approved' AND created_at::date = CURRENT_DATE
                        """));
        out.put("failed", count("SELECT COALESCE(SUM(failed), 0) FROM collect.runs WHERE started_at::date = CURRENT_DATE"));
        return out;
    }

    public List<Map<String, Object>> runs() {
        return jdbc.query(
                """
                SELECT id, started_at, finished_at, status, sources_total, kept, review_count, failed, pdfs, started_by
                FROM collect.runs
                ORDER BY started_at DESC
                LIMIT 30
                """,
                (rs, i) -> runRow(rs));
    }

    public Map<String, Object> run(String id) {
        List<Map<String, Object>> rows = jdbc.query(
                """
                SELECT id, started_at, finished_at, status, sources_total, kept, review_count, failed, pdfs, started_by
                FROM collect.runs WHERE id = ?
                """,
                (rs, i) -> runRow(rs),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> sourceResults(String runId) {
        return jdbc.query(
                """
                SELECT id, source_name, url, phase, outcome, kept, review_count, dropped, error, duration_ms
                FROM collect.source_results WHERE run_id = ? ORDER BY source_name
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("sourceName", rs.getString("source_name"));
                    row.put("url", rs.getString("url"));
                    row.put("phase", rs.getString("phase"));
                    row.put("outcome", rs.getString("outcome"));
                    row.put("kept", rs.getInt("kept"));
                    row.put("reviewCount", rs.getInt("review_count"));
                    row.put("dropped", rs.getInt("dropped"));
                    row.put("error", rs.getString("error"));
                    row.put("durationMs", rs.getLong("duration_ms"));
                    return row;
                },
                runId);
    }

    public List<Map<String, Object>> noticesForSource(String sourceResultId) {
        return jdbc.query(
                """
                SELECT id, title, disposition, wait_reason, last_date
                FROM collect.notices WHERE source_result_id = ? ORDER BY created_at
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("title", rs.getString("title"));
                    row.put("disposition", rs.getString("disposition"));
                    row.put("waitReason", rs.getString("wait_reason"));
                    Date last = rs.getDate("last_date");
                    row.put("lastDate", last == null ? "" : last.toLocalDate().toString());
                    return row;
                },
                sourceResultId);
    }

    public List<Map<String, Object>> queue(String status) {
        String review = switch (status == null ? "waiting" : status) {
            case "held", "rejected" -> status;
            default -> "waiting";
        };
        return jdbc.query(
                """
                SELECT n.id, n.title, n.organization, n.official_url, n.last_date, n.wait_reason,
                       s.source_name
                FROM collect.notices n
                LEFT JOIN collect.source_results s ON s.id = n.source_result_id
                WHERE n.review_status = ?
                ORDER BY n.created_at DESC
                LIMIT 80
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("title", rs.getString("title"));
                    row.put("organization", rs.getString("organization"));
                    row.put("officialUrl", rs.getString("official_url"));
                    row.put("waitReason", rs.getString("wait_reason"));
                    row.put("sourceName", rs.getString("source_name"));
                    Date last = rs.getDate("last_date");
                    row.put("lastDate", last == null ? "Missing" : last.toLocalDate().toString());
                    return row;
                },
                review);
    }

    public Map<String, Object> notice(String id) {
        List<Map<String, Object>> rows = jdbc.query(
                """
                SELECT n.id, n.title, n.organization, n.official_url, n.last_date, n.selection_process,
                       n.has_exam, n.excerpt, n.disposition, n.review_status, n.wait_reason, n.catalog_id,
                       n.run_id, s.source_name, r.started_at
                FROM collect.notices n
                LEFT JOIN collect.source_results s ON s.id = n.source_result_id
                LEFT JOIN collect.runs r ON r.id = n.run_id
                WHERE n.id = ?
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("title", rs.getString("title"));
                    row.put("organization", rs.getString("organization"));
                    row.put("officialUrl", rs.getString("official_url"));
                    Date last = rs.getDate("last_date");
                    row.put("lastDate", last == null ? "" : last.toLocalDate().toString());
                    row.put("selectionProcess", rs.getString("selection_process"));
                    row.put("hasExam", rs.getBoolean("has_exam"));
                    row.put("excerpt", rs.getString("excerpt"));
                    row.put("disposition", rs.getString("disposition"));
                    row.put("reviewStatus", rs.getString("review_status"));
                    row.put("waitReason", rs.getString("wait_reason"));
                    row.put("catalogId", rs.getString("catalog_id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("sourceName", rs.getString("source_name"));
                    row.put("startedAt", rs.getTimestamp("started_at"));
                    return row;
                },
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> keywords() {
        return jdbc.query(
                "SELECT id, phrase, sort_order FROM collect.keywords WHERE enabled ORDER BY sort_order, phrase",
                (rs, i) -> Map.of("id", rs.getString("id"), "phrase", rs.getString("phrase"), "order", rs.getInt("sort_order")));
    }

    public List<String> keywordPhrases() {
        return jdbc.query(
                "SELECT phrase FROM collect.keywords WHERE enabled ORDER BY sort_order, phrase",
                (rs, i) -> rs.getString("phrase"));
    }

    public List<Map<String, Object>> priorityLinks() {
        return jdbc.query(
                "SELECT id, url, label, sort_order FROM collect.priority_links WHERE enabled ORDER BY sort_order, label",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("url", rs.getString("url"));
                    row.put("label", rs.getString("label"));
                    row.put("order", rs.getInt("sort_order"));
                    return row;
                });
    }

    public void addKeyword(String phrase) {
        String clean = phrase == null ? "" : phrase.trim().toLowerCase();
        if (clean.length() < 2 || clean.length() > 40) {
            throw new StoreException("VALIDATION", "A keyword needs 2 to 40 characters");
        }
        int next = count("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM collect.keywords");
        jdbc.update(
                "INSERT INTO collect.keywords (id, phrase, sort_order) VALUES (?, ?, ?) ON CONFLICT (phrase) DO NOTHING",
                UUID.randomUUID().toString(),
                clean,
                next);
    }

    public void addLink(String url, String label) {
        String official = urls.requireAllowed(url).toString();
        String name = label == null || label.isBlank() ? hostLabel(official) : label.trim();
        int next = count("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM collect.priority_links");
        jdbc.update(
                """
                INSERT INTO collect.priority_links (id, url, label, sort_order)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (url) DO NOTHING
                """,
                UUID.randomUUID().toString(),
                official,
                name,
                next);
    }

    public String openRun(String startedBy, int sources) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO collect.runs (id, status, sources_total, started_by)
                VALUES (?, 'running', ?, ?)
                """,
                id,
                sources,
                startedBy == null ? "" : startedBy);
        return id;
    }

    public String openSource(String runId, String sourceName, String url) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO collect.source_results (id, run_id, source_name, url, phase, outcome)
                VALUES (?, ?, ?, ?, 'fetch', 'ok')
                """,
                id,
                runId,
                sourceName,
                url);
        return id;
    }

    public void saveNotice(
            String runId,
            String sourceResultId,
            String title,
            String organization,
            String officialUrl,
            LocalDate lastDate,
            String selection,
            boolean hasExam,
            String excerpt,
            String disposition,
            String waitReason) {
        String review = "dropped".equals(disposition) ? "rejected" : "waiting";
        jdbc.update(
                """
                INSERT INTO collect.notices
                  (id, run_id, source_result_id, title, organization, official_url, last_date,
                   selection_process, has_exam, excerpt, disposition, review_status, wait_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                runId,
                sourceResultId,
                title,
                organization,
                officialUrl,
                lastDate == null ? null : Date.valueOf(lastDate),
                selection == null ? "" : selection,
                hasExam,
                excerpt == null ? "" : excerpt,
                disposition,
                review,
                waitReason == null ? "" : waitReason);
    }

    public void finishSource(String sourceResultId, String outcome, int kept, int review, int dropped, String error, long durationMs) {
        jdbc.update(
                """
                UPDATE collect.source_results
                SET outcome = ?, kept = ?, review_count = ?, dropped = ?, error = ?, duration_ms = ?, phase = 'done'
                WHERE id = ?
                """,
                outcome,
                kept,
                review,
                dropped,
                error == null ? "" : error,
                durationMs,
                sourceResultId);
    }

    public void finishRun(String runId, String status) {
        Integer kept = jdbc.queryForObject("SELECT COALESCE(SUM(kept), 0) FROM collect.source_results WHERE run_id = ?", Integer.class, runId);
        Integer review = jdbc.queryForObject("SELECT COALESCE(SUM(review_count), 0) FROM collect.source_results WHERE run_id = ?", Integer.class, runId);
        Integer failed = jdbc.queryForObject("SELECT COUNT(*) FROM collect.source_results WHERE run_id = ? AND outcome = 'failed'", Integer.class, runId);
        jdbc.update(
                """
                UPDATE collect.runs
                SET status = ?, finished_at = ?, kept = ?, review_count = ?, failed = ?
                WHERE id = ?
                """,
                status,
                java.sql.Timestamp.from(Instant.now()),
                kept == null ? 0 : kept,
                review == null ? 0 : review,
                failed == null ? 0 : failed,
                runId);
    }

    @Transactional
    public void decide(String noticeId, String decision) {
        Map<String, Object> notice = notice(noticeId);
        if (notice == null) {
            throw new StoreException("NOT_FOUND", "Notice not found");
        }
        String current = String.valueOf(notice.get("reviewStatus"));
        if ("approved".equals(current) || "dropped".equals(notice.get("disposition"))) {
            throw new StoreException("VALIDATION", "This notice is already closed");
        }
        if ("approved".equals(decision)) {
            String catalogId = publish(notice);
            jdbc.update(
                    "UPDATE collect.notices SET review_status = 'approved', catalog_id = ? WHERE id = ?",
                    catalogId,
                    noticeId);
            return;
        }
        if ("held".equals(decision) || "rejected".equals(decision)) {
            jdbc.update("UPDATE collect.notices SET review_status = ? WHERE id = ?", decision, noticeId);
            return;
        }
        throw new StoreException("VALIDATION", "Decision must be approved, held, or rejected");
    }

    private String publish(Map<String, Object> notice) {
        String url = urls.requireAllowed(String.valueOf(notice.get("officialUrl"))).toString();
        String title = String.valueOf(notice.get("title"));
        String organization = String.valueOf(notice.get("organization"));
        String lastDate = String.valueOf(notice.get("lastDate"));
        String id = JobSchema.stableJobId(organization, title, lastDate, url);
        jdbc.update(
                """
                INSERT INTO catalog.opportunities
                  (id, title, organization, org_type, sector, location, qualification, selection_process,
                   has_exam, last_date, official_url, source_name, review_status, summary, sample)
                VALUES (?, ?, ?, 'central', '', 'All India', '', ?, ?, ?, ?, ?, 'approved', ?, FALSE)
                ON CONFLICT (id) DO UPDATE SET
                  review_status = 'approved',
                  title = EXCLUDED.title,
                  last_date = EXCLUDED.last_date,
                  summary = EXCLUDED.summary
                """,
                id,
                title,
                organization,
                String.valueOf(notice.get("selectionProcess")),
                Boolean.TRUE.equals(notice.get("hasExam")),
                lastDate.isBlank() ? null : Date.valueOf(lastDate),
                url,
                String.valueOf(notice.getOrDefault("sourceName", "crawl")),
                String.valueOf(notice.getOrDefault("excerpt", "")));
        return id;
    }

    private static Map<String, Object> runRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("startedAt", rs.getTimestamp("started_at"));
        row.put("finishedAt", rs.getTimestamp("finished_at"));
        row.put("status", rs.getString("status"));
        row.put("sourcesTotal", rs.getInt("sources_total"));
        row.put("kept", rs.getInt("kept"));
        row.put("reviewCount", rs.getInt("review_count"));
        row.put("failed", rs.getInt("failed"));
        row.put("pdfs", rs.getInt("pdfs"));
        row.put("startedBy", rs.getString("started_by"));
        return row;
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    private static String hostLabel(String url) {
        String host = java.net.URI.create(url).getHost();
        return host == null ? "Link" : host.replaceFirst("^www\\.", "");
    }
}
