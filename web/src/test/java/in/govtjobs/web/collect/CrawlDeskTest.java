package in.govtjobs.web.collect;

import static org.assertj.core.api.Assertions.assertThat;

import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.ops.OfficialUrlPolicy;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CrawlDeskTest {

    private JdbcTemplate jdbc;
    private CrawlDeskStore store;
    private CrawlRunner runner;

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
        store = new CrawlDeskStore(jdbc, new OfficialUrlPolicy(new GovtJobsProperties()));
        runner = new CrawlRunner(store, new CrawlJobLogger(jdbc), new OfficialUrlPolicy(new GovtJobsProperties()), uri -> {
            String body = """
                    <html><title>Stenographer Grade C and D</title>
                    <body>Recruitment notification. Last date 2027-10-14. Written test.</body></html>
                    """;
            if (uri.getHost().contains("ibps")) {
                throw new java.io.IOException("timed out");
            }
            if (uri.getHost().contains("rrb")) {
                body = "<html><title>Tender</title><body>Works contract for civil repair.</body></html>";
            }
            return new PageFetcher.PageFetch(200, body);
        });
    }

    @Test
    void crawlStaysHiddenUntilApproval() {
        jdbc.update("DELETE FROM catalog.opportunities WHERE title = 'Stenographer Grade C and D'");
        String runId = store.openRun("ada", 3);
        runner.execute(
                runId,
                List.of(
                        Map.of("label", "SSC", "url", "https://ssc.gov.in/"),
                        Map.of("label", "IBPS", "url", "https://www.ibps.in/"),
                        Map.of("label", "RRB", "url", "https://www.rrbcdg.gov.in/")));
        Map<String, Object> run = store.run(runId);
        assertThat(run.get("status")).isEqualTo("finished");
        assertThat(run.get("kept")).isEqualTo(1);
        assertThat(run.get("failed")).isEqualTo(1);
        assertThat(store.queue("waiting")).extracting(row -> row.get("title")).contains("Stenographer Grade C and D");
        Integer visible = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.opportunities WHERE title = 'Stenographer Grade C and D'",
                Integer.class);
        assertThat(visible).isZero();
        String noticeId = store.queue("waiting").stream()
                .filter(row -> "Stenographer Grade C and D".equals(row.get("title")))
                .map(row -> String.valueOf(row.get("id")))
                .findFirst()
                .orElseThrow();
        store.decide(noticeId, "approved");
        Integer approved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.opportunities WHERE title = 'Stenographer Grade C and D'",
                Integer.class);
        assertThat(approved).isZero();
        Map<String, Object> decided = jdbc.queryForMap(
                "SELECT review_status, catalog_id FROM collect.notices WHERE id = ?", noticeId);
        assertThat(decided.get("review_status")).isEqualTo("approved");
        assertThat(decided.get("catalog_id")).isNull();
        assertThat(store.queue("waiting")).extracting(row -> row.get("id")).doesNotContain(noticeId);
        List<Map<String, Object>> log = new CrawlJobLogger(jdbc).forRun(runId);
        assertThat(log).anyMatch(row -> String.valueOf(row.get("message")).contains("Stenographer"));
        assertThat(log).anyMatch(row -> "ERROR".equals(row.get("level")));
        store.addKeyword("apprentice");
        assertThat(store.keywordPhrases()).contains("apprentice");
        assertThat(CrawlRunner.titleOf("<title>Hello</title>", "fallback")).isEqualTo("Hello");
        assertThat(CrawlRunner.firstDate("no date")).isNull();
    }
}
