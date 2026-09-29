package in.govtjobs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.govtjobs.web.config.FeatureFlags;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.config.StudentDatabaseEnvironmentPostProcessor;
import in.govtjobs.web.mail.StudentMailService;
import in.govtjobs.web.store.CatalogStore;
import in.govtjobs.web.support.IndiaStates;
import in.govtjobs.web.support.JsonMaps;
import in.govtjobs.web.support.Maps;
import in.govtjobs.web.support.ViewAdvice;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class QualityGateUnitTest {

    @Test
    void statesAndMapHelpers() {
        assertThat(IndiaStates.STATES).hasSize(36);
        assertThat(IndiaStates.STATES.get("DL")).isEqualTo("Delhi");
        assertThat(IndiaStates.STATES.get("TG")).isEqualTo("Telangana");
        assertThat(JsonMaps.str(null, "a")).isNull();
        assertThat(JsonMaps.str(Map.of("a", "  "), "a")).isNull();
        assertThat(JsonMaps.str(Map.of("a", "null"), "a")).isNull();
        assertThat(JsonMaps.strOrEmpty(Map.of("a", "ok"), "a")).isEqualTo("ok");
        assertThat(JsonMaps.strOrEmpty(Map.of(), "missing")).isEmpty();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobs", List.of(Map.of("id", "1"), "skip"));
        assertThat(JsonMaps.listOf(data, "jobs")).hasSize(1);
        assertThat(JsonMaps.listOf(data, "missing")).isEmpty();
        data.put("meta", Map.of("k", 1));
        assertThat(JsonMaps.mapOf(data, "meta")).containsEntry("k", 1);
        assertThat(JsonMaps.mapOf(data, "absent")).isEmpty();
        assertThat(Maps.str(null, "a")).isNull();
        assertThat(Maps.bool(Map.of("a", "true"), "a")).isTrue();
        assertThat(Maps.bool(null, "a")).isFalse();
        assertThat(Maps.list(Map.of("a", new ArrayList<>(List.of("x"))), "a")).hasSize(1);
        assertThat(Maps.list(null, "a")).isEmpty();
        assertThat(Maps.list(Map.of("a", "nope"), "a")).isEmpty();
    }

    @Test
    void flagsPropertiesMailAndDatabaseUrl() {
        FeatureFlags flags = new FeatureFlags();
        flags.setStudent(false);
        flags.setServerMatch(false);
        flags.setPrepare(false);
        flags.setUnpublish(false);
        assertThat(flags.isStudent()).isFalse();
        assertThat(flags.isServerMatch()).isFalse();
        assertThat(flags.isPrepare()).isFalse();
        assertThat(flags.isUnpublish()).isFalse();

        GovtJobsProperties props = new GovtJobsProperties();
        props.getMail().setResendApiKey(" ");
        props.getSession().setCookieSecure(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailers = mock(ObjectProvider.class);
        when(mailers.getIfAvailable()).thenReturn(null);
        StudentMailService mail = new StudentMailService(props, mailers);
        assertThat(mail.provider()).isEqualTo("dev");
        assertThat(mail.allowDevLink()).isFalse();
        assertThat(mail.health()).containsEntry("provider", "dev");
        props.getSession().setCookieSecure(false);
        assertThat(mail.allowDevLink()).isTrue();
        props.getMail().setResendApiKey("re_test");
        assertThat(mail.provider()).isEqualTo("resend");
        assertThat(mail.allowDevLink()).isFalse();

        StudentDatabaseEnvironmentPostProcessor processor = new StudentDatabaseEnvironmentPostProcessor();
        SpringApplication app = new SpringApplication(QualityGateUnitTest.class);
        StandardEnvironment blank = new StandardEnvironment();
        processor.postProcessEnvironment(blank, app);
        assertThat(blank.getPropertySources().contains("student-database-url")).isFalse();

        StandardEnvironment jdbc = env("STUDENT_DATABASE_URL", "jdbc:postgresql://db/govtjobs");
        processor.postProcessEnvironment(jdbc, app);
        assertThat(jdbc.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db/govtjobs");

        StandardEnvironment tc = env("STUDENT_DATABASE_URL", "postgres://u:p@h/db");
        tc.getPropertySources().addFirst(new MapPropertySource("t", Map.of("spring.datasource.url", "jdbc:tc:postgresql:govtjobs")));
        processor.postProcessEnvironment(tc, app);
        assertThat(tc.getProperty("spring.datasource.url")).startsWith("jdbc:tc:");

        StandardEnvironment pg = env("STUDENT_DATABASE_URL", "postgres://ada:secret@db.internal:5433/students");
        processor.postProcessEnvironment(pg, app);
        assertThat(pg.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.internal:5433/students");
        assertThat(pg.getProperty("spring.datasource.username")).isEqualTo("ada");
        assertThat(pg.getProperty("spring.datasource.password")).isEqualTo("secret");

        StandardEnvironment userOnly = env("STUDENT_DATABASE_URL", "postgresql://ada@db.internal/students");
        processor.postProcessEnvironment(userOnly, app);
        assertThat(userOnly.getProperty("spring.datasource.username")).isEqualTo("ada");

        StandardEnvironment broken = env("STUDENT_DATABASE_URL", "postgres://[");
        processor.postProcessEnvironment(broken, app);
        assertThat(broken.getPropertySources().contains("student-database-url")).isFalse();
    }

    @Test
    void viewAdviceRoles() {
        FeatureFlags flags = new FeatureFlags();
        GovtJobsProperties props = new GovtJobsProperties();
        props.getCatalog().setSource("postgres");
        CatalogStore catalog = mock(CatalogStore.class);
        when(catalog.sampleMode()).thenReturn(true);
        ViewAdvice advice = new ViewAdvice(flags, props, catalog);
        assertThat(advice.sampleCatalog()).isTrue();
        assertThat(advice.flags()).isSameAs(flags);
        assertThat(advice.orgTypeLabel()).isNotEmpty();
        assertThat(advice.selectionLabel()).isNotEmpty();
        assertThat(advice.qualLabel()).isNotEmpty();
        assertThat(advice.statusLabel()).isNotEmpty();
        assertThat(advice.hasExamLabel()).isNotEmpty();
        assertThat(advice.currentEmail(null)).isNull();
        assertThat(advice.isOps(null)).isFalse();
        assertThat(advice.isStudent(null)).isFalse();
        assertThat(advice.isAdmin(null)).isFalse();
        var auth = new UsernamePasswordAuthenticationToken(
                "ada@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(advice.currentEmail(auth)).isEqualTo("ada@example.com");
        assertThat(advice.isStudent(auth)).isTrue();
        assertThat(advice.isAdmin(auth)).isTrue();
        assertThat(advice.isOps(auth)).isFalse();
        props.getCatalog().setSource("json");
        assertThat(advice.sampleCatalog()).isFalse();
    }

    private static StandardEnvironment env(String key, String value) {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        environment.getPropertySources().addFirst(new MapPropertySource("test", map));
        return environment;
    }

    @Test
    void propertiesRoundTrip() {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getOps().setUserAgent("bot");
        props.getOps().setConnectTimeoutSeconds(3);
        props.getOps().setRequestTimeoutSeconds(4);
        props.getOps().setMaxBodyBytes(100);
        props.getOps().setAllowedSuffixes(new ArrayList<>(List.of(".gov.in")));
        props.getOps().setExtraHosts(new ArrayList<>(List.of("ibps.in")));
        props.getStudent().setPasswordMin(12);
        props.getStudent().setMaxFileBytes(10);
        props.getStudent().setFilesDir("files");
        props.getStudent().setImportJson(false);
        props.getSession().setStudentTtlDays(2);
        props.getSession().setOpsTtlHours(3);
        props.getMail().setFrom("desk@example.com");
        props.getMail().setPublicSiteUrl("https://example.com/");
        props.getMail().setResetTtlHours(2);
        props.getCatalog().setPageSize(8);
        props.getCatalog().setSeedDummy(true);
        props.getRateLimit().setWindowMinutes(5);
        props.getRateLimit().setLoginMax(9);
        props.getRateLimit().setCollectMax(11);
        assertThat(props.getOps().getUserAgent()).isEqualTo("bot");
        assertThat(props.getOps().getAllowedSuffixes()).contains(".gov.in");
        assertThat(props.getStudent().getPasswordMin()).isEqualTo(12);
        assertThat(props.getStudent().isImportJson()).isFalse();
        assertThat(props.getSession().getStudentTtlDays()).isEqualTo(2);
        assertThat(props.getMail().getResetTtlHours()).isEqualTo(2);
        assertThat(props.getCatalog().getPageSize()).isEqualTo(8);
        assertThat(props.getCatalog().isSeedDummy()).isTrue();
        assertThat(props.getRateLimit().getLoginMax()).isEqualTo(9);
        assertThat(props.getRateLimit().getCollectMax()).isEqualTo(11);
        assertThat(props.getStudent().getFilesDir()).isEqualTo("files");
        assertThat(props.getStudent().getMaxFileBytes()).isEqualTo(10);
        assertThat(props.getOps().getConnectTimeoutSeconds()).isEqualTo(3);
        assertThat(props.getSession().getOpsTtlHours()).isEqualTo(3);
        assertThat(props.getMail().getFrom()).isEqualTo("desk@example.com");
    }
}
