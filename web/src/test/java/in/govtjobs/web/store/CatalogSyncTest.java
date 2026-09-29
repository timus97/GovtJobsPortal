package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.web.config.GovtJobsProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CatalogSyncTest {

    private static final String FIXTURE_ID = "a567e4b783a647af";
    private static final String HTTP_JOB_ID = "17396a3ae7db4aa6";

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
    void postgresSyncLoadsGitCatalogAndKeepsSamplesOutOfThePublicList() {
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
            assertThat(jobs).hasSize(27);
            assertThat(jobs).noneMatch(row -> Boolean.TRUE.equals(row.get("sample")));
            assertThat(jobs).extracting(row -> row.get("id")).contains(FIXTURE_ID, HTTP_JOB_ID).doesNotContain(sampleId);
            Map<String, Object> fixture = jobs.stream().filter(row -> FIXTURE_ID.equals(row.get("id"))).findFirst().orElseThrow();
            assertThat(fixture.get("title")).isEqualTo("Combined Graduate Level Examination (fixture)");
            assertThat(fixture.get("sourceId")).isEqualTo("seed_manual");
            assertThat(fixture.get("vacancies")).isEqualTo("7000");
            assertThat(fixture.get("notificationDate")).isEqualTo("2026-08-01");
            assertThat(fixture.get("officialUrl")).isEqualTo("https://ssc.gov.in/apply/cgl-2026");
            assertThat(fixture.get("sample")).isEqualTo(false);
            Map<String, Object> httpJob = jobs.stream().filter(row -> HTTP_JOB_ID.equals(row.get("id"))).findFirst().orElseThrow();
            assertThat(String.valueOf(httpJob.get("officialUrl"))).startsWith("http://");
            assertThat(store.sampleMode()).isFalse();

            List<Map<String, Object>> series = store.approvedSeries();
            assertThat(series).hasSize(25);
            assertThat(series).noneMatch(row -> Boolean.TRUE.equals(row.get("sample")));
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
            Map<String, Object> parsed = store.approvedJobs().stream()
                    .filter(row -> badDateId.equals(row.get("id")))
                    .findFirst()
                    .orElseThrow();
            assertThat(parsed.get("lastDate")).isEqualTo("");
            assertThat(parsed.get("status")).isEqualTo("open");

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
