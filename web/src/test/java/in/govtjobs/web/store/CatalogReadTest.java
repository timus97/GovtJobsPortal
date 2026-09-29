package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;

import in.govtjobs.web.config.GovtJobsProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CatalogReadTest {

    private JdbcTemplate jdbc;
    private CatalogStore store;

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
        GovtJobsProperties props = new GovtJobsProperties();
        props.getCatalog().setSeedDummy(false);
        store = new CatalogStore(jdbc, props);
    }

    @Test
    void readsApprovedRowsAndSkipsSeedWhenDisabled() {
        store.seedIfEmpty();
        assertThat(store.approvedJobs()).allSatisfy(row -> {
            assertThat(row).containsKeys(
                    "id", "title", "status", "vacancies", "officialUrl", "sourceId", "notificationDate");
            assertThat(String.valueOf(row.get("officialUrl"))).isNotBlank();
        });
        assertThat(store.approvedSeries()).allSatisfy(row -> assertThat(row).containsKeys(
                "id", "name", "linkedOpportunityIds", "minEducation", "applyNever"));
        assertThat(store.sampleMode()).isNotNull();
    }

    @Test
    void seedDoesNotInsertWhenRowsAlreadyExist() {
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.opportunities", Integer.class);
        GovtJobsProperties props = new GovtJobsProperties();
        props.getCatalog().setSeedDummy(true);
        CatalogStore seeding = new CatalogStore(jdbc, props);
        seeding.seedIfEmpty();
        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.opportunities", Integer.class);
        if (before != null && before > 0) {
            assertThat(after).isEqualTo(before);
        } else {
            assertThat(after).isGreaterThan(0);
            assertThat(seeding.approvedJobs()).noneMatch(row -> "sample-hidden-review".equals(row.get("id")));
        }
    }
}
