package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.web.config.GovtJobsProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CatalogSyncTest {

    private static final String FIXTURE_ID = "a567e4b783a647af";
    private static final String CLOSED_SAMPLE_ID = "9f00ebbcb5f9f38e";
    private static final Set<String> SAMPLE_SEED_IDS = Set.of(FIXTURE_ID, CLOSED_SAMPLE_ID);

    private JdbcTemplate jdbc;
    private GovtJobsProperties props;
    private CatalogStore store;
    private String prefix;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:postgresql://127.0.0.1:5432/govtjobs_students");
        ds.setUsername("govtjobs");
        ds.setPassword("govtjobs");
        ds.setDriverClassName("org.postgresql.Driver");
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(ds);
        props = new GovtJobsProperties();
        props.getCatalog().setSource("postgres");
        props.getCatalog().setSeedDummy(false);
        store = new CatalogStore(jdbc, props);
        prefix = "phasea-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void deleteRowsThisTestInserted() {
        jdbc.update("DELETE FROM catalog.exam_series WHERE id LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM catalog.opportunities WHERE id LIKE ?", prefix + "%");
    }

    @Test
    void postgresSyncLoadsGitCatalogAndKeepsSamplesOutOfThePublicList() throws Exception {
        String marker = prefix + "-marker";
        String sampleId = prefix + "-sample";
        String seriesMarker = prefix + "-series";
        insertJob(marker, false);
        insertJob(sampleId, true);
        insertSeries(seriesMarker);
        try {
            props.getCatalog().setSource("json");
            new CatalogSync(store, props).loadGitCatalog();
            assertThat(countJobs(marker)).isEqualTo(1);
            assertThat(countSeries(seriesMarker)).isEqualTo(1);

            assertThat(store.replacePublishedJobs(null)).isZero();
            assertThat(store.replacePublishedJobs(List.of())).isZero();
            assertThat(store.replacePublishedJobs(List.of(
                    Map.of("id", "", "officialUrl", "https://ssc.gov.in/"),
                    Map.of("id", prefix + "-skip", "officialUrl", " "))))
                    .isZero();
            assertThat(store.replacePublishedSeries(null)).isZero();
            assertThat(store.replacePublishedSeries(List.of(
                    Map.of("id", prefix + "-skip", "officialUrl", ""))))
                    .isZero();
            assertThat(countJobs(marker)).isEqualTo(1);
            assertThat(countSeries(seriesMarker)).isEqualTo(1);

            props.getCatalog().setSource("postgres");
            new CatalogSync(store, props).loadGitCatalog();
            assertThat(countJobs(marker)).isZero();
            assertThat(countSeries(seriesMarker)).isZero();
            assertThat(countJobs(sampleId)).isEqualTo(1);
            assertThat(store.removeLiveJob(sampleId)).isFalse();
            assertThat(countJobs(sampleId)).isEqualTo(1);

            List<Map<String, Object>> jobs = store.approvedJobs();
            // Publishable file rows sync. Sample seeds, a bad date, and a non-https URL do not.
            assertThat(jobs).extracting(row -> String.valueOf(row.get("id")))
                    .containsExactlyInAnyOrderElementsOf(publishableJobIds())
                    .isNotEmpty()
                    .doesNotContain(FIXTURE_ID, CLOSED_SAMPLE_ID, sampleId);
            assertThat(jobs).noneMatch(row -> Boolean.TRUE.equals(row.get("sample")));
            assertThat(jobs).allMatch(row -> String.valueOf(row.get("officialUrl")).toLowerCase(Locale.ROOT).startsWith("https://"));
            assertThat(jobs).noneMatch(row -> String.valueOf(row.get("lastDate")).isBlank() && "open".equals(row.get("status")));
            Map<String, Object> fixture = jdbc.queryForMap(
                    """
                    SELECT title, source_id, vacancies, to_char(notification_date, 'YYYY-MM-DD') AS notified,
                           official_url, sample
                    FROM catalog.opportunities WHERE id = ?
                    """,
                    FIXTURE_ID);
            assertThat(fixture.get("title")).isEqualTo("Combined Graduate Level Examination (fixture)");
            assertThat(fixture.get("source_id")).isEqualTo("seed_manual");
            assertThat(fixture.get("vacancies")).isEqualTo("7000");
            assertThat(fixture.get("notified")).isEqualTo("2026-08-01");
            assertThat(fixture.get("official_url")).isEqualTo("https://ssc.gov.in/apply/cgl-2026");
            assertThat(fixture.get("sample")).isEqualTo(true);
            assertThat(jdbc.queryForObject(
                    "SELECT sample FROM catalog.opportunities WHERE id = ?", Boolean.class, CLOSED_SAMPLE_ID))
                    .isTrue();
            assertThat(store.sampleMode()).isFalse();

            List<Map<String, Object>> series = store.approvedSeries();
            assertThat(series).extracting(row -> String.valueOf(row.get("id")))
                    .containsExactlyInAnyOrderElementsOf(publishedIds("exam_series.json"));
            assertThat(series).noneMatch(row -> Boolean.TRUE.equals(row.get("sample")));
            assertThat(series).allMatch(row -> String.valueOf(row.get("officialUrl")).startsWith("https://"));
            Map<String, Object> ctet = series.stream().filter(row -> "ctet".equals(row.get("id"))).findFirst().orElseThrow();
            assertThat(ctet.get("applyNever")).isEqualTo(true);
            assertThat(ctet.get("minEducation")).isEqualTo("12th");
            assertThat(ctet.get("sourceId")).isEqualTo("ctet_calendar");

            Integer zonal = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM collect.priority_links WHERE url = ?",
                    Integer.class,
                    "https://www.rrbcdg.gov.in/");
            Integer national = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM collect.priority_links WHERE url = ?",
                    Integer.class,
                    "https://www.rrbapply.gov.in/");
            assertThat(zonal).isZero();
            assertThat(national).isEqualTo(1);

            String reviewId = prefix + "-review";
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("id", reviewId);
            review.put("title", "Phase A needs review");
            review.put("organization", "Staff Selection Commission");
            review.put("officialUrl", "https://ssc.gov.in/phase-a-review");
            review.put("needsReview", true);
            store.upsertLiveJob(review);
            assertThat(store.approvedJobs()).extracting(row -> row.get("id")).doesNotContain(reviewId);
            assertThat(jdbc.queryForObject(
                    "SELECT review_status FROM catalog.opportunities WHERE id = ?", String.class, reviewId))
                    .isEqualTo("needs_review");

            String badDateId = prefix + "-baddate";
            Map<String, Object> badDate = new LinkedHashMap<>();
            badDate.put("id", badDateId);
            badDate.put("title", "Phase A unparsed date");
            badDate.put("organization", "Staff Selection Commission");
            badDate.put("officialUrl", "https://ssc.gov.in/phase-a-baddate");
            badDate.put("lastDate", "not-a-date");
            store.upsertLiveJob(badDate);
            String missingDateId = prefix + "-nodate";
            Map<String, Object> missingDate = new LinkedHashMap<>();
            missingDate.put("id", missingDateId);
            missingDate.put("title", "Phase A missing date");
            missingDate.put("organization", "Staff Selection Commission");
            missingDate.put("officialUrl", "https://ssc.gov.in/phase-a-nodate");
            store.upsertLiveJob(missingDate);
            String httpId = prefix + "-http";
            Map<String, Object> http = new LinkedHashMap<>();
            http.put("id", httpId);
            http.put("title", "Phase A plain http");
            http.put("organization", "Staff Selection Commission");
            http.put("officialUrl", "http://ssc.gov.in/phase-a-http");
            http.put("lastDate", "2030-01-15");
            store.upsertLiveJob(http);
            String pastId = prefix + "-past";
            Map<String, Object> past = new LinkedHashMap<>();
            past.put("id", pastId);
            past.put("title", "Phase A past date");
            past.put("organization", "Staff Selection Commission");
            past.put("officialUrl", "https://ssc.gov.in/phase-a-past");
            past.put("lastDate", "2020-01-02");
            store.upsertLiveJob(past);
            List<Map<String, Object>> afterRules = store.approvedJobs();
            assertThat(afterRules).extracting(row -> row.get("id"))
                    .doesNotContain(badDateId, missingDateId, httpId)
                    .contains(pastId);
            Map<String, Object> closed = afterRules.stream()
                    .filter(row -> pastId.equals(row.get("id")))
                    .findFirst()
                    .orElseThrow();
            assertThat(closed.get("status")).isEqualTo("closed");
            assertThat(afterRules).allMatch(row -> String.valueOf(row.get("officialUrl")).toLowerCase(Locale.ROOT).startsWith("https://"));

            String unknownExamId = prefix + "-nullexam";
            Map<String, Object> unknownExam = new LinkedHashMap<>();
            unknownExam.put("id", unknownExamId);
            unknownExam.put("title", "Phase A unknown exam");
            unknownExam.put("organization", "Staff Selection Commission");
            unknownExam.put("officialUrl", "https://ssc.gov.in/phase-a-nullexam");
            unknownExam.put("lastDate", "2030-04-01");
            store.upsertLiveJob(unknownExam);
            Map<String, Object> unknownRow = store.approvedJobs().stream()
                    .filter(row -> unknownExamId.equals(row.get("id")))
                    .findFirst()
                    .orElseThrow();
            assertThat(unknownRow.get("hasExam")).isNull();
            JobStore reader = new JobStore(new ObjectMapper());
            JobStore.JobQuery noExam = JobStore.JobQuery.from(
                    null, null, null, null, null, "open", null, "no", null, null, 1, 20);
            assertThat(reader.applyFilters(List.of(unknownRow), noExam)).isEmpty();

            store.upsertLiveJob(null);
            store.upsertLiveJob(Map.of("id", prefix + "-blank", "title", "missing url"));
            store.upsertLiveJob(Map.of("officialUrl", "https://ssc.gov.in/phase-a-noid", "title", "missing id"));
            assertThat(countJobs(prefix + "-blank")).isZero();
            assertThat(store.removeLiveJob(null)).isFalse();
            assertThat(store.removeLiveJob("")).isFalse();
        } finally {
            jdbc.update("DELETE FROM catalog.opportunities WHERE id LIKE ?", prefix + "%");
            jdbc.update("DELETE FROM catalog.exam_series WHERE id LIKE ?", prefix + "%");
        }
    }

    @Test
    void pasteWritesTheJsonFileAndThePostgresRowThenUnpublishRemovesBoth() throws Exception {
        Path jobs = RepoPaths.data().resolve("processed").resolve("jobs.json");
        Path opps = RepoPaths.data().resolve("processed").resolve("opportunities.json");
        byte[] jobBytes = readIfExists(jobs);
        byte[] oppBytes = readIfExists(opps);
        String id = prefix + "-paste";
        Path paste = RepoPaths.data().resolve("staging").resolve("ops_paste").resolve(id + ".json");
        JobStore jobStore = new JobStore(new ObjectMapper(), store, props);
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("title", "Phase A pasted notice");
            row.put("organization", "Staff Selection Commission");
            row.put("orgType", "central");
            row.put("officialUrl", "https://ssc.gov.in/phase-a-paste-test");
            row.put("sourceId", "ops_paste");
            row.put("sourceName", "Ops paste");
            row.put("sourceUrl", "https://ssc.gov.in/phase-a-paste-test");
            row.put("lastDate", "2030-01-15");
            row.put("summary", "Phase A paste dual-write check");

            jobStore.upsertPublishedJob(row);
            Map<String, Object> stored = jobStore.getJobById(id);
            assertThat(stored).isNotNull();
            assertThat(stored.get("title")).isEqualTo("Phase A pasted notice");
            assertThat(stored.get("sourceId")).isEqualTo("ops_paste");
            assertThat(stored.get("officialUrl")).isEqualTo("https://ssc.gov.in/phase-a-paste-test");
            assertThat(Files.readString(jobs)).contains(id);
            assertThat(Files.isRegularFile(paste)).isTrue();
            assertThat(countJobs(id)).isEqualTo(1);

            assertThat(jobStore.removePublishedJob(id)).isTrue();
            assertThat(jobStore.getJobById(id)).isNull();
            assertThat(countJobs(id)).isZero();
            assertThat(Files.readString(jobs)).doesNotContain(id);
            assertThat(Files.exists(paste)).isFalse();
            assertThat(jobStore.removePublishedJob(id)).isFalse();
        } finally {
            restore(jobs, jobBytes);
            restore(opps, oppBytes);
            store.removeLiveJob(id);
            Files.deleteIfExists(paste);
        }
    }

    private void insertJob(String id, boolean sample) {
        jdbc.update(
                """
                INSERT INTO catalog.opportunities
                  (id, title, organization, org_type, sector, location, qualification, selection_process,
                   has_exam, last_date, official_url, source_name, review_status, summary, sample)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'Phase A',?,'',?)
                """,
                id, "Phase A marker", "Board", "central", "Other", "Delhi", "graduate",
                "cbt", true, java.sql.Date.valueOf("2031-02-02"), "https://ssc.gov.in/", "approved", sample);
    }

    private void insertSeries(String id) {
        jdbc.update(
                """
                INSERT INTO catalog.exam_series
                  (id, name, board, cycle, apply_never, official_url, expected_exam, review_status, linked_ids, summary, sample)
                VALUES (?,?,?,?,?,?,NULL,'approved','','',FALSE)
                """,
                id, "Phase A series", "COV", "2027", false, "https://ssc.gov.in/");
    }

    private int countJobs(String id) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.opportunities WHERE id = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private int countSeries(String id) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.exam_series WHERE id = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private static List<String> publishedIds(String fileName) throws java.io.IOException {
        List<Map<String, Object>> rows = new ObjectMapper().readValue(
                RepoPaths.data().resolve("processed").resolve(fileName).toFile(),
                new TypeReference<List<Map<String, Object>>>() {});
        return rows.stream().map(row -> String.valueOf(row.get("id"))).toList();
    }

    private static List<String> publishableJobIds() throws java.io.IOException {
        List<Map<String, Object>> rows = new ObjectMapper().readValue(
                RepoPaths.data().resolve("processed").resolve("jobs.json").toFile(),
                new TypeReference<List<Map<String, Object>>>() {});
        return rows.stream()
                .filter(row -> !SAMPLE_SEED_IDS.contains(String.valueOf(row.get("id"))))
                .filter(row -> String.valueOf(row.get("officialUrl")).toLowerCase(Locale.ROOT).startsWith("https://"))
                .filter(row -> publishableDate(row.get("lastDate")))
                .map(row -> String.valueOf(row.get("id")))
                .toList();
    }

    private static boolean publishableDate(Object value) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        if (raw.length() != 10 || raw.charAt(4) != '-' || raw.charAt(7) != '-') {
            return false;
        }
        try {
            LocalDate.parse(raw);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static byte[] readIfExists(Path path) throws java.io.IOException {
        return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
    }

    private static void restore(Path path, byte[] bytes) throws java.io.IOException {
        if (bytes == null) {
            Files.deleteIfExists(path);
        } else {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        }
    }
}
