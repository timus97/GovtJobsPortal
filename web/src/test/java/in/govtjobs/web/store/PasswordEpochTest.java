package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.security.PasswordService;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PasswordEpochTest {

    private StudentStore store;

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
        store = new StudentStore(
                new ObjectMapper(), new PasswordService(), new JobStore(new ObjectMapper()), new GovtJobsProperties(), new JdbcTemplate(ds), null);
    }

    @Test
    void passwordChangeBumpsSessionEpoch() {
        Map<String, Object> student = store.register("epoch-" + UUID.randomUUID() + "@example.com", "password1234");
        String id = String.valueOf(student.get("id"));
        assertThat(store.sessionEpoch(id)).isZero();
        store.setPassword(id, "password5678");
        assertThat(store.sessionEpoch(id)).isEqualTo(1);
        store.setPassword(id, "password9012");
        assertThat(store.sessionEpoch(id)).isEqualTo(2);
        assertThat(store.sessionEpoch("missing")).isZero();
    }
}
