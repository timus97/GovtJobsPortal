package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;

import in.govtjobs.web.config.GovtJobsProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CatalogStoreTest {

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
        prefix = "cov-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void deleteOnlyRowsThisTestInserted() {
        jdbc.update("DELETE FROM catalog.exam_series WHERE id LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM catalog.opportunities WHERE id LIKE ?", prefix + "%");
    }

    @Test
    void seedIfEmptyReturnsWithoutWritingWhenDisabledOrCatalogAlreadyHasRows() {
        props.getCatalog().setSeedDummy(false);
        store.seedIfEmpty();
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.opportunities", Integer.class);
        props.getCatalog().setSeedDummy(true);
        if (before != null && before > 0) {
            store.seedIfEmpty();
            Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.opportunities", Integer.class);
            assertThat(after).isEqualTo(before);
        } else {
            store.seedIfEmpty();
            Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.opportunities", Integer.class);
            assertThat(after).isNotNull().isGreaterThan(0);
            assertThat(store.approvedJobs()).extracting(row -> row.get("id")).contains("sample-ssc-cgl");
            assertThat(store.approvedSeries()).extracting(row -> row.get("id")).contains("sample-gate");
            assertThat(store.approvedJobs())
                    .extracting(row -> row.get("id"))
                    .doesNotContain("sample-hidden-review");
        }
    }

    @Test
    void approvedListsHideNeedsReviewAndKeepMissingDatesAndBlankLinks() {
        insertJob(prefix + "-open", "Coverage open post", "Open Board", "central", "CovSector", "Pune", "graduate",
                "written_multi_stage", true, "2030-06-01", "approved", false);
        insertJob(prefix + "-sample-only", "Coverage sample only", "Sample Board", "central", "CovSector", "Pune",
                "graduate", "cbt", true, "2030-06-01", "approved", true);
        jdbc.update("UPDATE catalog.opportunities SET source_id = 'sample' WHERE id = ?", prefix + "-open");
        insertJob(prefix + "-soon", "Coverage soon post", "Soon Board", "state", "CovSector", "Pune City", "12th",
                "cbt", true, java.time.LocalDate.now().plusDays(2).toString(), "approved", false);
        insertJob(prefix + "-closed", "Coverage closed post", "Closed Board", "psu", "Other", "Delhi", "diploma",
                "interview_only", false, "2020-01-01", "approved", false);
        insertJob(prefix + "-review", "Coverage hidden post", "Hidden Board", "central", "CovSector", "Pune",
                "graduate", "interview_only", false, "2030-01-01", "needs_review", true);
        jdbc.update(
                """
                INSERT INTO catalog.opportunities
                  (id, title, organization, org_type, sector, location, qualification, selection_process,
                   has_exam, last_date, official_url, source_name, review_status, summary, sample)
                VALUES (?,?,?,?,?,?,?,?,?,NULL,?,'Sample catalog','approved','',FALSE)
                """,
                prefix + "-nodate", "No date post", "Board", "central", "CovSector", "", "graduate",
                "written_multi_stage", true, "https://ssc.gov.in/");
        jdbc.update(
                """
                INSERT INTO catalog.exam_series
                  (id, name, board, cycle, apply_never, official_url, expected_exam, review_status, linked_ids, summary, sample)
                VALUES (?,?,?,?,?,? ,NULL,'approved',?,?,FALSE)
                """,
                prefix + "-series", "Coverage series", "COVBOARD", "2027", false, "https://ssc.gov.in/",
                " , " + prefix + "-open,, " + prefix + "-closed", "linked sample");
        jdbc.update(
                """
                INSERT INTO catalog.exam_series
                  (id, name, board, cycle, apply_never, official_url, expected_exam, review_status, linked_ids, summary, sample)
                VALUES (?,?,?,?,?,?,NULL,'needs_review','','hidden',FALSE)
                """,
                prefix + "-series-hidden", "Hidden series", "COVBOARD", "", true, "https://ssc.gov.in/");

        List<Map<String, Object>> jobs = store.approvedJobs();
        assertThat(jobs).extracting(row -> row.get("id"))
                .contains(prefix + "-open", prefix + "-soon", prefix + "-closed", prefix + "-nodate")
                .doesNotContain(prefix + "-review", prefix + "-sample-only");
        assertThat(jobs).noneMatch(row -> Boolean.TRUE.equals(row.get("sample")));
        assertThat(store.sampleMode()).isFalse();
        Map<String, Object> open = jobs.stream().filter(row -> (prefix + "-open").equals(row.get("id"))).findFirst().orElseThrow();
        assertThat(open.get("orgType")).isEqualTo("central");
        assertThat(open.get("hasExam")).isEqualTo(true);
        assertThat(open.get("lastDate")).isEqualTo("2030-06-01");
        assertThat(open.get("status")).isEqualTo("open");
        assertThat(open.get("sourceId")).isEqualTo("sample");
        assertThat(open.get("sample")).isEqualTo(false);
        Map<String, Object> nodate = jobs.stream().filter(row -> (prefix + "-nodate").equals(row.get("id"))).findFirst().orElseThrow();
        assertThat(nodate.get("lastDate")).isEqualTo("");
        assertThat(nodate.get("status")).isEqualTo("open");

        List<Map<String, Object>> series = store.approvedSeries();
        assertThat(series).extracting(row -> row.get("id"))
                .contains(prefix + "-series")
                .doesNotContain(prefix + "-series-hidden");
        Map<String, Object> row = series.stream().filter(s -> (prefix + "-series").equals(s.get("id"))).findFirst().orElseThrow();
        assertThat(row.get("expectedExam")).isEqualTo("");
        assertThat(row.get("linkedOpportunityIds")).isEqualTo(List.of(prefix + "-open", prefix + "-closed"));
        assertThat(row.get("applyNever")).isEqualTo(false);
    }

    @Test
    void sampleModeIsTrueOnlyWhileThePublicCatalogIsSamples() {
        Integer real = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.opportunities WHERE sample = FALSE AND review_status = 'approved'",
                Integer.class);
        insertJob(prefix + "-sample", "Sample flag", "Board", "central", "CovSector", "Pune", "graduate",
                "cbt", true, "2031-01-01", "approved", true);
        if (real != null && real > 0) {
            assertThat(store.sampleMode()).isFalse();
            assertThat(store.approvedJobs()).noneMatch(row -> Boolean.TRUE.equals(row.get("sample")));
        } else {
            assertThat(store.sampleMode()).isTrue();
            assertThat(store.approvedJobs()).extracting(row -> row.get("id")).contains(prefix + "-sample");
        }
    }

    @Test
    void blankSourceIdOnASampleReadsAsSample() {
        assertThat(CatalogStore.sourceIdFor(null, true)).isEqualTo("sample");
        assertThat(CatalogStore.sourceIdFor("  ", true)).isEqualTo("sample");
        assertThat(CatalogStore.sourceIdFor(null, false)).isEmpty();
        assertThat(CatalogStore.sourceIdFor("seed_manual", true)).isEqualTo("seed_manual");
        assertThat(CatalogStore.sourceUrlFor(null, "https://ssc.gov.in/")).isEqualTo("https://ssc.gov.in/");
        assertThat(CatalogStore.sourceUrlFor("  ", null)).isEmpty();
        assertThat(CatalogStore.sourceUrlFor("https://ssc.gov.in/a", "https://ssc.gov.in/"))
                .isEqualTo("https://ssc.gov.in/a");
    }

    private void insertJob(
            String id, String title, String org, String orgType, String sector, String location, String qual,
            String selection, boolean hasExam, String last, String review, boolean sample) {
        jdbc.update(
                """
                INSERT INTO catalog.opportunities
                  (id, title, organization, org_type, sector, location, qualification, selection_process,
                   has_exam, last_date, official_url, source_name, review_status, summary, sample)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'Sample catalog',?,?,?)
                """,
                id, title, org, orgType, sector, location, qual, selection, hasExam,
                java.sql.Date.valueOf(last), "https://ssc.gov.in/", review, "summary", sample);
    }
}
