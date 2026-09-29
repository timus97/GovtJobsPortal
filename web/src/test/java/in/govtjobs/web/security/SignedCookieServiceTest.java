package in.govtjobs.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignedCookieServiceTest {

    private SignedCookieService service;

    @BeforeEach
    void setUp() {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getSession().setSecret("unit-test-secret-unit-test-secret");
        service = new SignedCookieService(new ObjectMapper(), props);
        service.init();
    }

    @Test
    void studentTokenRoundTripsAndRejectsOpsAudience() {
        String token = service.signStudent("sid-1", "a@example.com", 0);
        SignedCookieService.Payload payload = service.parseStudent(token);
        assertThat(payload).isNotNull();
        assertThat(payload.aud()).isEqualTo("student");
        assertThat(payload.uid()).isEqualTo("sid-1");
        assertThat(payload.epoch()).isZero();
        assertThat(service.parseOps(token)).isNull();
        assertThat(service.parseStudent(null)).isNull();
        assertThat(service.parseStudent("not-a-token")).isNull();
        assertThat(service.parseStudent("")).isNull();
        assertThat(service.parseStudent(token + "x")).isNull();
    }

    @Test
    void secureCookiesRejectAMissingSecret() {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getSession().setCookieSecure(true);
        SignedCookieService secure = new SignedCookieService(new ObjectMapper(), props);
        assertThatThrownBy(secure::init).isInstanceOf(IllegalStateException.class).hasMessageContaining("SESSION_SECRET");
    }
}
