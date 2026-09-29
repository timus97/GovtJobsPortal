package in.govtjobs.web.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.domain.job.JobSchema;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.support.JsonFiles;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogStore {

    private static final Logger log = LoggerFactory.getLogger(CatalogStore.class);
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP = new TypeReference<>() {};
    private static final String OPPORTUNITIES = "catalog.opportunities";
    private static final String SERIES = "catalog.exam_series";

    private static final String JOB_UPSERT = """
            INSERT INTO catalog.opportunities
              (id, title, organization, org_type, sector, location, qualification, selection_process,
               has_exam, last_date, official_url, source_name, source_id, source_url, vacancies,
               notification_date, review_status, summary, sample)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE)
            ON CONFLICT (id) DO UPDATE SET
              title = EXCLUDED.title,
              organization = EXCLUDED.organization,
              org_type = EXCLUDED.org_type,
              sector = EXCLUDED.sector,
              location = EXCLUDED.location,
              qualification = EXCLUDED.qualification,
              selection_process = EXCLUDED.selection_process,
              has_exam = EXCLUDED.has_exam,
              last_date = EXCLUDED.last_date,
              official_url = EXCLUDED.official_url,
              source_name = EXCLUDED.source_name,
              source_id = EXCLUDED.source_id,
              source_url = EXCLUDED.source_url,
              vacancies = EXCLUDED.vacancies,
              notification_date = EXCLUDED.notification_date,
              review_status = EXCLUDED.review_status,
              summary = EXCLUDED.summary,
              sample = FALSE
            """;

    private static final String SERIES_UPSERT = """
            INSERT INTO catalog.exam_series
              (id, name, board, cycle, apply_never, official_url, expected_exam, review_status,
               linked_ids, summary, source_id, min_education, sample)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE)
            ON CONFLICT (id) DO UPDATE SET
              name = EXCLUDED.name,
              board = EXCLUDED.board,
              cycle = EXCLUDED.cycle,
              apply_never = EXCLUDED.apply_never,
              official_url = EXCLUDED.official_url,
              expected_exam = EXCLUDED.expected_exam,
              review_status = EXCLUDED.review_status,
              linked_ids = EXCLUDED.linked_ids,
              summary = EXCLUDED.summary,
              source_id = EXCLUDED.source_id,
              min_education = EXCLUDED.min_education,
              sample = FALSE
            """;

    private final JdbcTemplate jdbc;
    private final GovtJobsProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public CatalogStore(JdbcTemplate jdbc, GovtJobsProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @PostConstruct
    public void seedIfEmpty() {
        if (!props.getCatalog().isSeedDummy()) {
            return;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.opportunities", Integer.class);
        if (n != null && n > 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        insertJob("sample-ssc-cgl", "Combined Graduate Level — sample window", "Staff Selection Commission",
                "central", "Administration", "All India", "graduate", "written_multi_stage", true,
                today.plusDays(18), "https://ssc.gov.in/", "Open sample. Check the official SSC notice before you act.",
                "approved");
        insertJob("sample-ibps-po", "Probationary Officer — sample window", "Institute of Banking Personnel Selection",
                "central", "Banking", "All India", "graduate", "written_multi_stage", true,
                today.plusDays(4), "https://www.ibps.in/", "Closing soon in this sample. The date is illustrative.",
                "approved");
        insertJob("sample-rrb-ntpc", "NTPC — sample window", "Railway Recruitment Boards",
                "central", "Railways", "All India", "12th", "cbt", true,
                today.plusDays(40), "https://www.rrbapply.gov.in/", "Sample only. Open the official RRB notice before you act.",
                "approved");
        insertJob("sample-bel-walkin", "Walk-in interview — sample", "Bharat Electronics Limited",
                "psu", "Defence", "Bengaluru", "diploma", "walk_in", false,
                today.plusDays(12), "https://bel-india.in/", "No written exam in this sample card. Confirm the walk-in notice.",
                "approved");
        insertJob("sample-esic-closed", "Nursing Officer — sample, already closed", "Employees' State Insurance Corporation",
                "central", "Health", "Delhi", "graduate", "written_multi_stage", true,
                today.minusDays(12), "https://www.esic.gov.in/", "Closed sample so the desk can show a finished window.",
                "approved");
        insertJob("sample-hidden-review", "Needs admin approval — must stay hidden", "Sample Board",
                "central", "Other", "Delhi", "graduate", "interview_only", false,
                today.plusDays(30), "https://www.india.gov.in/", "This row is waiting for an admin. Students must not see it.",
                "needs_review");

        insertSeries("sample-ssc-cgl-series", "SSC CGL", "SSC", "Sample cycle", false,
                "https://ssc.gov.in/", today.plusDays(80), "sample-ssc-cgl",
                "A calendar row. Apply only appears when a linked sample window is still open.");
        insertSeries("sample-gate", "GATE", "IIT", "Sample cycle", true,
                "https://gate2027.iitm.ac.in/", today.plusDays(100), "",
                "Prepare-for sample. This is a score exam, not a vacancy.");
        insertSeries("sample-upsc-cse", "UPSC CSE", "UPSC", "Sample cycle", false,
                "https://upsc.gov.in/", today.plusDays(140), "",
                "No linked open window in the sample, so there is no Apply button.");
    }

    /**
     * Upserts the git catalog, then deletes non-sample rows whose ids left the file.
     * A missing or empty file is a no-op so a bad read cannot wipe the live copy.
     */
    @Transactional
    public void syncFromGit() {
        int jobs = replacePublishedJobs(readGit("jobs.json"));
        int series = replacePublishedSeries(readGit("exam_series.json"));
        log.info("catalog.sync jobs={} series={}", jobs, series);
    }

    @Transactional
    public void upsertLiveJob(Map<String, Object> row) {
        if (row == null) {
            return;
        }
        List<LiveJob> jobs = liveJobs(List.of(row));
        if (jobs.isEmpty()) {
            return;
        }
        upsertJobs(jobs);
    }

    @Transactional
    public boolean removeLiveJob(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return jdbc.update(
                "DELETE FROM catalog.opportunities WHERE id = ? AND sample = FALSE", id) > 0;
    }

    int replacePublishedJobs(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            log.info("catalog.sync.skipped table={} reason={}", OPPORTUNITIES, rows == null ? "missing" : "empty");
            return 0;
        }
        List<LiveJob> jobs = liveJobs(rows);
        if (jobs.isEmpty()) {
            log.info("catalog.sync.skipped table={} reason=empty", OPPORTUNITIES);
            return 0;
        }
        upsertJobs(jobs);
        int removed = deleteMissing(OPPORTUNITIES, jobs.stream().map(LiveJob::id).toList());
        log.info("catalog.sync.jobs upserted={} removed={}", jobs.size(), removed);
        return jobs.size();
    }

    int replacePublishedSeries(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            log.info("catalog.sync.skipped table={} reason={}", SERIES, rows == null ? "missing" : "empty");
            return 0;
        }
        List<LiveSeries> series = liveSeries(rows);
        if (series.isEmpty()) {
            log.info("catalog.sync.skipped table={} reason=empty", SERIES);
            return 0;
        }
        upsertSeries(series);
        int removed = deleteMissing(SERIES, series.stream().map(LiveSeries::id).toList());
        log.info("catalog.sync.series upserted={} removed={}", series.size(), removed);
        return series.size();
    }

    public List<Map<String, Object>> approvedJobs() {
        return jdbc.query(
                """
                SELECT id, title, organization, org_type, sector, location, qualification,
                       selection_process, has_exam, last_date, official_url, source_name, source_id,
                       source_url, vacancies, notification_date, summary, sample
                FROM catalog.opportunities
                WHERE review_status = 'approved'
                  AND (sample = FALSE OR NOT EXISTS (
                        SELECT 1 FROM catalog.opportunities real_row
                        WHERE real_row.sample = FALSE AND real_row.review_status = 'approved'))
                ORDER BY last_date NULLS LAST
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Date last = rs.getDate("last_date");
                    Date notified = rs.getDate("notification_date");
                    String lastDate = last == null ? "" : last.toLocalDate().toString();
                    boolean sample = rs.getBoolean("sample");
                    String vacancies = rs.getString("vacancies");
                    row.put("id", rs.getString("id"));
                    row.put("title", rs.getString("title"));
                    row.put("organization", rs.getString("organization"));
                    row.put("orgType", rs.getString("org_type"));
                    row.put("sector", rs.getString("sector"));
                    row.put("location", rs.getString("location"));
                    row.put("qualification", rs.getString("qualification"));
                    row.put("selectionProcess", rs.getString("selection_process"));
                    row.put("hasExam", rs.getBoolean("has_exam"));
                    row.put("lastDate", lastDate);
                    row.put("officialUrl", rs.getString("official_url"));
                    row.put("sourceName", rs.getString("source_name"));
                    row.put("sourceId", sourceIdFor(rs.getString("source_id"), sample));
                    row.put("sourceUrl", sourceUrlFor(rs.getString("source_url"), rs.getString("official_url")));
                    row.put("summary", rs.getString("summary"));
                    row.put("sample", sample);
                    row.put("vacancies", vacancies == null ? "" : vacancies);
                    row.put("notificationDate", notified == null ? "" : notified.toLocalDate().toString());
                    row.put("status", JobSchema.computeStatus(lastDate));
                    return row;
                });
    }

    public List<Map<String, Object>> approvedSeries() {
        return jdbc.query(
                """
                SELECT id, name, board, cycle, apply_never, official_url, expected_exam, linked_ids,
                       summary, sample, source_id, min_education
                FROM catalog.exam_series
                WHERE review_status = 'approved'
                  AND (sample = FALSE OR NOT EXISTS (
                        SELECT 1 FROM catalog.exam_series real_row
                        WHERE real_row.sample = FALSE AND real_row.review_status = 'approved'))
                ORDER BY name
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Date exam = rs.getDate("expected_exam");
                    String sourceId = rs.getString("source_id");
                    String minEducation = rs.getString("min_education");
                    row.put("id", rs.getString("id"));
                    row.put("name", rs.getString("name"));
                    row.put("board", rs.getString("board"));
                    row.put("cycle", rs.getString("cycle"));
                    row.put("applyNever", rs.getBoolean("apply_never"));
                    row.put("officialUrl", rs.getString("official_url"));
                    row.put("expectedExam", exam == null ? "" : exam.toLocalDate().toString());
                    row.put("expectedNotify", "");
                    row.put("expectedApply", "");
                    row.put("sourceId", sourceId == null ? "" : sourceId);
                    row.put("minEducation", minEducation == null ? "" : minEducation);
                    row.put("summary", rs.getString("summary"));
                    row.put("sample", rs.getBoolean("sample"));
                    List<String> ids = new ArrayList<>();
                    String linked = rs.getString("linked_ids");
                    if (linked != null && !linked.isBlank()) {
                        for (String part : linked.split(",")) {
                            if (!part.isBlank()) {
                                ids.add(part.trim());
                            }
                        }
                    }
                    row.put("linkedOpportunityIds", ids);
                    return row;
                });
    }

    public boolean sampleMode() {
        Integer real = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.opportunities WHERE sample = FALSE AND review_status = 'approved'",
                Integer.class);
        if (real != null && real > 0) {
            return false;
        }
        Integer samples = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.opportunities WHERE sample = TRUE AND review_status = 'approved'",
                Integer.class);
        return samples != null && samples > 0;
    }

    static String sourceIdFor(String sourceId, boolean sample) {
        if (sourceId == null || sourceId.isBlank()) {
            return sample ? "sample" : "";
        }
        return sourceId;
    }

    static String sourceUrlFor(String sourceUrl, String officialUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return officialUrl == null ? "" : officialUrl;
        }
        return sourceUrl;
    }

    private List<Map<String, Object>> readGit(String name) {
        Path path = RepoPaths.data().resolve("processed").resolve(name);
        return JsonFiles.read(path, mapper, LIST_MAP, null, log);
    }

    private List<LiveJob> liveJobs(List<Map<String, Object>> rows) {
        List<LiveJob> jobs = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String url = text(row.get("officialUrl"));
            if (id.isBlank() || url.isBlank()) {
                continue;
            }
            jobs.add(new LiveJob(
                    id,
                    text(row.get("title")),
                    text(row.get("organization")),
                    text(row.get("orgType")),
                    text(row.get("sector")),
                    text(row.get("location")),
                    text(row.get("qualification")),
                    text(row.get("selectionProcess")),
                    flag(row.get("hasExam")),
                    parseDate(row.get("lastDate")),
                    url,
                    text(row.get("sourceName")),
                    text(row.get("sourceId")),
                    text(row.get("sourceUrl")),
                    text(row.get("vacancies")),
                    parseDate(row.get("notificationDate")),
                    flag(row.get("needsReview")) ? "needs_review" : "approved",
                    text(row.get("summary"))));
        }
        return jobs;
    }

    private List<LiveSeries> liveSeries(List<Map<String, Object>> rows) {
        List<LiveSeries> series = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String url = text(row.get("officialUrl"));
            if (id.isBlank() || url.isBlank()) {
                continue;
            }
            series.add(new LiveSeries(
                    id,
                    text(row.get("name")),
                    text(row.get("board")),
                    text(row.get("cycle")),
                    flag(row.get("applyNever")),
                    url,
                    parseDate(row.get("expectedExam")),
                    flag(row.get("needsReview")) ? "needs_review" : "approved",
                    linkedIds(row.get("linkedOpportunityIds")),
                    text(row.get("summary")),
                    text(row.get("sourceId")),
                    text(row.get("minEducation"))));
        }
        return series;
    }

    private void upsertJobs(List<LiveJob> jobs) {
        jdbc.batchUpdate(JOB_UPSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LiveJob job = jobs.get(i);
                ps.setString(1, job.id());
                ps.setString(2, job.title());
                ps.setString(3, job.organization());
                ps.setString(4, job.orgType());
                ps.setString(5, job.sector());
                ps.setString(6, job.location());
                ps.setString(7, job.qualification());
                ps.setString(8, job.selectionProcess());
                ps.setBoolean(9, job.hasExam());
                setDate(ps, 10, job.lastDate());
                ps.setString(11, job.officialUrl());
                ps.setString(12, job.sourceName());
                ps.setString(13, job.sourceId());
                ps.setString(14, job.sourceUrl());
                ps.setString(15, job.vacancies());
                setDate(ps, 16, job.notificationDate());
                ps.setString(17, job.reviewStatus());
                ps.setString(18, job.summary());
            }

            @Override
            public int getBatchSize() {
                return jobs.size();
            }
        });
    }

    private void upsertSeries(List<LiveSeries> series) {
        jdbc.batchUpdate(SERIES_UPSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LiveSeries row = series.get(i);
                ps.setString(1, row.id());
                ps.setString(2, row.name());
                ps.setString(3, row.board());
                ps.setString(4, row.cycle());
                ps.setBoolean(5, row.applyNever());
                ps.setString(6, row.officialUrl());
                setDate(ps, 7, row.expectedExam());
                ps.setString(8, row.reviewStatus());
                ps.setString(9, row.linkedIds());
                ps.setString(10, row.summary());
                ps.setString(11, row.sourceId());
                ps.setString(12, row.minEducation());
            }

            @Override
            public int getBatchSize() {
                return series.size();
            }
        });
    }

    private int deleteMissing(String table, List<String> ids) {
        // Postgres treats `id <> ALL('{}')` as true for every row.
        if (ids.isEmpty()) {
            return 0;
        }
        if (!OPPORTUNITIES.equals(table) && !SERIES.equals(table)) {
            throw new IllegalArgumentException("Unexpected catalog table");
        }
        Integer removed = jdbc.execute((Connection con) -> {
            java.sql.Array array = con.createArrayOf("text", ids.toArray(String[]::new));
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM " + table + " WHERE sample = FALSE AND id <> ALL (?)")) {
                ps.setArray(1, array);
                return ps.executeUpdate();
            } finally {
                array.free();
            }
        });
        return removed == null ? 0 : removed;
    }

    private static void setDate(PreparedStatement ps, int index, Date value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, value);
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static boolean flag(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(text(value));
    }

    private static Date parseDate(Object value) {
        String raw = text(value);
        if (raw.length() >= 10) {
            raw = raw.substring(0, 10);
        }
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Date.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String linkedIds(Object value) {
        if (!(value instanceof List<?> list)) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (Object item : list) {
            String part = text(item);
            if (part.isBlank()) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append(',');
            }
            joined.append(part);
        }
        return joined.toString();
    }

    private void insertJob(
            String id, String title, String org, String orgType, String sector, String location, String qual,
            String selection, boolean hasExam, LocalDate last, String url, String summary, String review) {
        jdbc.update(
                """
                INSERT INTO catalog.opportunities
                  (id, title, organization, org_type, sector, location, qualification, selection_process,
                   has_exam, last_date, official_url, source_name, review_status, summary, sample)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'Sample catalog',?,?,TRUE)
                """,
                id, title, org, orgType, sector, location, qual, selection, hasExam,
                Date.valueOf(last), url, review, summary);
    }

    private void insertSeries(
            String id, String name, String board, String cycle, boolean applyNever, String url,
            LocalDate expected, String linked, String summary) {
        jdbc.update(
                """
                INSERT INTO catalog.exam_series
                  (id, name, board, cycle, apply_never, official_url, expected_exam, review_status, linked_ids, summary, sample)
                VALUES (?,?,?,?,?,?,?,'approved',?,?,TRUE)
                """,
                id, name, board, cycle, applyNever, url, Date.valueOf(expected), linked, summary);
    }

    private record LiveJob(
            String id,
            String title,
            String organization,
            String orgType,
            String sector,
            String location,
            String qualification,
            String selectionProcess,
            boolean hasExam,
            Date lastDate,
            String officialUrl,
            String sourceName,
            String sourceId,
            String sourceUrl,
            String vacancies,
            Date notificationDate,
            String reviewStatus,
            String summary) {}

    private record LiveSeries(
            String id,
            String name,
            String board,
            String cycle,
            boolean applyNever,
            String officialUrl,
            Date expectedExam,
            String reviewStatus,
            String linkedIds,
            String summary,
            String sourceId,
            String minEducation) {}
}
