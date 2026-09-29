package in.govtjobs.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

class StudentDatabaseEnvironmentPostProcessorTest {

    private final StudentDatabaseEnvironmentPostProcessor processor = new StudentDatabaseEnvironmentPostProcessor();

    @Test
    void blankUrlAndTestcontainersUrlAreLeftAlone() {
        MockEnvironment blank = new MockEnvironment().withProperty("STUDENT_DATABASE_URL", "  ");
        processor.postProcessEnvironment(blank, new SpringApplication());
        assertThat(blank.getProperty("spring.datasource.url")).isNull();

        MockEnvironment missing = new MockEnvironment();
        processor.postProcessEnvironment(missing, new SpringApplication());
        assertThat(missing.getProperty("spring.datasource.url")).isNull();

        MockEnvironment tc = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:tc:postgresql:16:///govtjobs")
                .withProperty("STUDENT_DATABASE_URL", "postgres://alice:secret@db:5432/students");
        processor.postProcessEnvironment(tc, new SpringApplication());
        assertThat(tc.getProperty("spring.datasource.url")).isEqualTo("jdbc:tc:postgresql:16:///govtjobs");
        assertThat(tc.getProperty("spring.datasource.username")).isNull();
    }

    @Test
    void jdbcUrlIsCopiedWithoutCredentials() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://old/db")
                .withProperty("STUDENT_DATABASE_URL", "jdbc:postgresql://db.internal:5432/students");
        processor.postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.internal:5432/students");
        assertThat(env.getProperty("spring.datasource.username")).isNull();
        assertThat(env.getProperty("spring.datasource.password")).isNull();
    }

    @Test
    void postgresUriMapsUserPasswordHostPortAndDatabase() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(new MapPropertySource(
                        "test",
                        Map.of("STUDENT_DATABASE_URL", "postgres://alice:s3cret@db.internal:5433/students")));
        processor.postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.internal:5433/students");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("alice");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("s3cret");
    }

    @Test
    void postgresqlSchemeUserWithoutPasswordAndDefaults() {
        MockEnvironment userOnly = new MockEnvironment()
                .withProperty("STUDENT_DATABASE_URL", "postgresql://onlyuser@localhost/mydb");
        processor.postProcessEnvironment(userOnly, new SpringApplication());
        assertThat(userOnly.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        assertThat(userOnly.getProperty("spring.datasource.username")).isEqualTo("onlyuser");
        assertThat(userOnly.getProperty("spring.datasource.password")).isEqualTo("govtjobs");

        MockEnvironment defaults = new MockEnvironment().withProperty("STUDENT_DATABASE_URL", "postgres://dbhost");
        processor.postProcessEnvironment(defaults, new SpringApplication());
        assertThat(defaults.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dbhost:5432/govtjobs_students");
        assertThat(defaults.getProperty("spring.datasource.username")).isEqualTo("govtjobs");
        assertThat(defaults.getProperty("spring.datasource.password")).isEqualTo("govtjobs");

        MockEnvironment emptyUser = new MockEnvironment()
                .withProperty("STUDENT_DATABASE_URL", "postgres://@dbhost:5432");
        processor.postProcessEnvironment(emptyUser, new SpringApplication());
        assertThat(emptyUser.getProperty("spring.datasource.username")).isEqualTo("govtjobs");
        assertThat(emptyUser.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dbhost:5432/govtjobs_students");

        MockEnvironment colonFirst = new MockEnvironment()
                .withProperty("STUDENT_DATABASE_URL", "postgres://:pw@dbhost/app");
        processor.postProcessEnvironment(colonFirst, new SpringApplication());
        assertThat(colonFirst.getProperty("spring.datasource.username")).isEmpty();
        assertThat(colonFirst.getProperty("spring.datasource.password")).isEqualTo("pw");
    }

    @Test
    void badUriAndNonPostgresSchemeDoNotInstallProperties() {
        MockEnvironment bad = new MockEnvironment().withProperty("STUDENT_DATABASE_URL", "postgres://%zz");
        processor.postProcessEnvironment(bad, new SpringApplication());
        assertThat(bad.getProperty("spring.datasource.url")).isNull();
        assertThat(bad.getPropertySources().contains("student-database-url")).isFalse();

        Map<String, Object> raw = new HashMap<>();
        raw.put("STUDENT_DATABASE_URL", "mysql://user:pass@host:3306/db");
        StandardEnvironment other = new StandardEnvironment();
        other.getPropertySources().addFirst(new MapPropertySource("test", raw));
        processor.postProcessEnvironment(other, new SpringApplication());
        assertThat(other.getProperty("spring.datasource.url")).isNull();
        assertThat(other.getPropertySources().contains("student-database-url")).isFalse();
    }
}
