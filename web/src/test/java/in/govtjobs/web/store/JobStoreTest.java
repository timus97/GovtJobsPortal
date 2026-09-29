package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class JobStoreTest {

    private JdbcTemplate jdbc;
    private GovtJobsProperties props;
    private CatalogStore catalog;
    private String prefix;
    private final ObjectMapper mapper = new ObjectMapper();

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
        catalog = new CatalogStore(jdbc, props);
        prefix = "covj-" + UUID.randomUUID().toString().substring(0, 8);
        String openLast = java.time.LocalDate.now().plusDays(40).toString();
        String soonLast = java.time.LocalDate.now().plusDays(3).toString();
        insertJob(prefix + "-open", prefix + " Coverage Alpha Clerk", "Alpha Org", "central", prefix, "Pune", "graduate",
                "written_multi_stage", true, openLast, "approved");
        insertJob(prefix + "-soon", prefix + " Coverage Beta Nurse", "Beta Org", "state", prefix, "Mumbai", "12th",
                "cbt", false, soonLast, "approved");
        insertJob(prefix + "-closed", prefix + " Coverage Gamma Closed", "Gamma Org", "psu", "Railways", "Delhi", "diploma",
                "interview_only", true, "2020-01-02", "approved");
        insertJob(prefix + "-review", prefix + " Coverage Hidden", "Hidden Org", "central", prefix, "Pune", "graduate",
                "cbt", true, openLast, "needs_review");
        jdbc.update(
                """
                INSERT INTO catalog.exam_series
                  (id, name, board, cycle, apply_never, official_url, expected_exam, review_status, linked_ids, summary, sample)
                VALUES (?,?,?,?,?,?,?,'approved',?,?,FALSE)
                """,
                prefix + "-series", "Coverage Alpha Series", "COVBOARD", "cycle-a", false, "https://ssc.gov.in/",
                java.sql.Date.valueOf(openLast),
                prefix + "-open," + prefix + "-closed," + prefix + "-missing",
                "series summary");
        jdbc.update(
                """
                INSERT INTO catalog.exam_series
                  (id, name, board, cycle, apply_never, official_url, expected_exam, review_status, linked_ids, summary, sample)
                VALUES (?,?,?,?,?,?,NULL,'approved','',?,FALSE)
                """,
                prefix + "-never", "Never Apply", "OTHER", "cycle-b", true, "https://upsc.gov.in/", "no window");
    }

    @AfterEach
    void deleteOnlyRowsThisTestInserted() {
        jdbc.update("DELETE FROM catalog.exam_series WHERE id LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM catalog.opportunities WHERE id LIKE ?", prefix + "%");
    }

    @Test
    void jsonCatalogReadsListsAndHidesNothingFromPostgres() {
        JobStore json = new JobStore(mapper);
        JobStore withNullProps = new JobStore(mapper, catalog, null);
        assertThat(json.catalogHealth().get("source")).isEqualTo("json");
        assertThat(withNullProps.catalogHealth().get("source")).isEqualTo("json");
        assertThat(json.getJobs()).isNotNull();
        assertThat(json.getJobs()).isSameAs(json.getJobs());
        assertThat(json.getOpportunities()).isNotNull();
        assertThat(json.getStats()).isNotNull();
        assertThat(json.getRegistry()).isNotNull();
        assertThat(json.getJobById(null)).isNull();
        assertThat(json.getJobById("missing-" + prefix)).isNull();
        assertThat(json.getExamSeriesById(null)).isNull();
        assertThat(json.getExamSeriesRaw()).isNotNull();
        assertThat(json.filterOptions()).containsKeys("orgTypes", "statuses", "boards", "hasExam");
        Map<String, Object> page = json.listJobs(JobStore.JobQuery.from(
                " ", null, null, null, null, null, null, null, null, null, null, null));
        assertThat(page).containsKeys("items", "total", "page", "limit", "totalPages");
        assertThat(page.get("page")).isEqualTo(1);
        assertThat(page.get("limit")).isEqualTo(12);
        assertThat(json.listExamSeries(" ", " ")).isNotNull();
    }

    @Test
    void postgresCatalogFiltersSearchAndDecoratesSeries() {
        props.getCatalog().setSource("POSTGRES");
        JobStore store = new JobStore(mapper, catalog, props);
        assertThat(store.catalogHealth())
                .containsEntry("source", "postgres")
                .containsKey("sample");
        assertThat(store.getJobs()).extracting(row -> row.get("id")).contains(prefix + "-open").doesNotContain(prefix + "-review");
        assertThat(store.getOpportunities()).extracting(row -> row.get("id")).contains(prefix + "-open");
        assertThat(store.getJobById(prefix + "-open")).isNotNull();
        assertThat(store.getJobById(prefix + "-review")).isNull();

        Map<String, Object> searched = store.listJobs(new JobStore.JobQuery(
                prefix,
                "central,state",
                "pun",
                "graduate",
                prefix,
                "open,closing_soon",
                "written_multi_stage",
                "yes",
                "sample",
                "lastDate",
                1,
                10));
        assertThat(searched.get("total")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) searched.get("items");
        assertThat(items).extracting(row -> row.get("id")).containsExactly(prefix + "-open");

        assertThat(store.listJobs(new JobStore.JobQuery(
                        null, null, null, null, prefix, null, null, "no", null, "newest", 1, 20))
                .get("total")).isEqualTo(1);
        assertThat(store.listJobs(new JobStore.JobQuery(
                        null, null, null, null, prefix, "closed", null, "true", null, "lastDate", 1, 5))
                .get("total")).isEqualTo(0);
        Map<String, Object> pastEnd = store.listJobs(new JobStore.JobQuery(
                null, null, null, null, prefix, "open", null, "1", null, "newest", 9, 0));
        assertThat(pastEnd.get("items")).isEqualTo(List.of());
        assertThat(pastEnd.get("limit")).isEqualTo(1);
        assertThat(store.listJobs(new JobStore.JobQuery(
                        null, null, null, null, prefix, null, null, "false", null, "lastDate", 0, 500))
                .get("limit")).isEqualTo(100);
        assertThat(store.listJobs(new JobStore.JobQuery(
                        null, null, null, null, "missing-sector", null, null, "maybe", "other-source", "lastDate", 1, 5))
                .get("total")).isEqualTo(0);

        Map<String, Object> options = store.filterOptions();
        assertThat(options.get("sectors")).asList().contains(prefix);
        assertThat(options.get("boards")).asList().contains("COVBOARD");

        List<Map<String, Object>> series = store.listExamSeries("covboard", "alpha");
        assertThat(series).hasSize(1);
        assertThat(series.get(0).get("canApply")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> linked = (List<Map<String, Object>>) series.get(0).get("linkedOpportunities");
        assertThat(linked).extracting(row -> row.get("id")).containsExactly(prefix + "-open");
        assertThat(store.getExamSeriesById(prefix + "-never").get("canApply")).isEqualTo(false);
        assertThat(store.listExamSeries("nobody", null)).isEmpty();
        assertThat(store.listExamSeries(null, "no-such-cycle-term")).isEmpty();
    }

    @Test
    void missingJsonFilesUseEmptySnapshotsAndBadJsonFailsClosed() throws Exception {
        Path processed = RepoPaths.data().resolve("processed");
        Path stats = processed.resolve("stats.json");
        Path opps = processed.resolve("opportunities.json");
        Path jobs = processed.resolve("jobs.json");
        byte[] statsBytes = readIfExists(stats);
        byte[] oppBytes = readIfExists(opps);
        byte[] jobBytes = readIfExists(jobs);
        try {
            Files.deleteIfExists(stats);
            Files.deleteIfExists(opps);
            JobStore empty = new JobStore(mapper);
            assertThat(empty.getStats()).containsEntry("total", 0);
            assertThat(empty.getOpportunities()).hasSize(empty.getJobs().size());
            Files.writeString(jobs, "{");
            assertThatThrownBy(() -> new JobStore(mapper).getJobs()).isInstanceOf(IllegalStateException.class);
        } finally {
            restore(stats, statsBytes);
            restore(opps, oppBytes);
            restore(jobs, jobBytes);
        }
    }

    @Test
    void upsertRejectsUnsafeIdsAndRoundTripsWithoutLeavingTheRow() throws Exception {
        Path jobs = RepoPaths.data().resolve("processed").resolve("jobs.json");
        Path opps = RepoPaths.data().resolve("processed").resolve("opportunities.json");
        byte[] jobBytes = readIfExists(jobs);
        byte[] oppBytes = readIfExists(opps);
        String id = "covpaste-" + UUID.randomUUID();
        Path paste = RepoPaths.data().resolve("staging").resolve("ops_paste").resolve(id + ".json");
        JobStore store = new JobStore(mapper);
        try {
            assertThatThrownBy(() -> store.upsertPublishedJob(null))
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining("id");
            assertThatThrownBy(() -> store.upsertPublishedJob(Map.of("title", "x")))
                    .isInstanceOf(StoreException.class);
            assertThatThrownBy(() -> store.upsertPublishedJob(Map.of("id", "../etc")))
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining("safe");
            assertThat(store.removePublishedJob(null)).isFalse();
            assertThat(store.removePublishedJob("../etc")).isFalse();
            assertThat(store.removePublishedJob("missing-" + id)).isFalse();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("title", "Paste coverage");
            row.put("organization", "Coverage");
            assertThat(store.upsertPublishedJob(row)).containsEntry("id", id);
            assertThat(store.getJobById(id).get("title")).isEqualTo("Paste coverage");
            assertThat(Files.isRegularFile(paste)).isTrue();
            row.put("title", "Paste replaced");
            store.upsertPublishedJob(row);
            assertThat(store.getJobs().get(0).get("id")).isEqualTo(id);
            assertThat(store.removePublishedJob(id)).isTrue();
            assertThat(store.getJobById(id)).isNull();
            assertThat(store.removePublishedJob(id)).isFalse();
            assertThat(Files.exists(paste)).isFalse();
        } finally {
            restore(jobs, jobBytes);
            restore(opps, oppBytes);
            Files.deleteIfExists(paste);
        }
    }

    private void insertJob(
            String id, String title, String org, String orgType, String sector, String location, String qual,
            String selection, boolean hasExam, String last, String review) {
        jdbc.update(
                """
                INSERT INTO catalog.opportunities
                  (id, title, organization, org_type, sector, location, qualification, selection_process,
                   has_exam, last_date, official_url, source_name, review_status, summary, sample)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'Sample catalog',?,?,FALSE)
                """,
                id, title, org, orgType, sector, location, qual, selection, hasExam,
                java.sql.Date.valueOf(last), "https://ssc.gov.in/", review, "summary text");
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
