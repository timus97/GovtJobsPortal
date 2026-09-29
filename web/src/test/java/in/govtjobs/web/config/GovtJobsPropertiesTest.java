package in.govtjobs.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GovtJobsPropertiesTest {

    @Test
    void nestedGettersAndSettersRoundTrip() {
        GovtJobsProperties props = new GovtJobsProperties();
        assertThat(props.getOps()).isNotNull();
        assertThat(props.getStudent()).isNotNull();
        assertThat(props.getSession()).isNotNull();
        assertThat(props.getMail()).isNotNull();
        assertThat(props.getCatalog()).isNotNull();
        assertThat(props.getRateLimit()).isNotNull();

        GovtJobsProperties.Ops ops = props.getOps();
        assertThat(ops.getAllowedSuffixes()).contains(".gov.in", ".nic.in");
        assertThat(ops.getExtraHosts()).contains("ibps.in", "ongcindia.com");
        assertThat(ops.getUserAgent()).isEqualTo("NoExamSarkariBot/1.0");
        assertThat(ops.getConnectTimeoutSeconds()).isEqualTo(15);
        assertThat(ops.getRequestTimeoutSeconds()).isEqualTo(20);
        assertThat(ops.getMaxBodyBytes()).isEqualTo(512 * 1024);
        ops.setAllowedSuffixes(List.of(".example"));
        ops.setExtraHosts(List.of("example.org"));
        ops.setUserAgent("TestBot");
        ops.setConnectTimeoutSeconds(3);
        ops.setRequestTimeoutSeconds(4);
        ops.setMaxBodyBytes(2048);
        assertThat(ops.getAllowedSuffixes()).containsExactly(".example");
        assertThat(ops.getExtraHosts()).containsExactly("example.org");
        assertThat(ops.getUserAgent()).isEqualTo("TestBot");
        assertThat(ops.getConnectTimeoutSeconds()).isEqualTo(3);
        assertThat(ops.getRequestTimeoutSeconds()).isEqualTo(4);
        assertThat(ops.getMaxBodyBytes()).isEqualTo(2048);

        GovtJobsProperties.Student student = props.getStudent();
        assertThat(student.getPasswordMin()).isEqualTo(10);
        assertThat(student.getMaxFileBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(student.getDataDir()).isEmpty();
        assertThat(student.getFilesDir()).isEmpty();
        assertThat(student.isImportJson()).isTrue();
        student.setPasswordMin(12);
        student.setMaxFileBytes(9L);
        student.setDataDir("data");
        student.setFilesDir("files");
        student.setImportJson(false);
        assertThat(student.getPasswordMin()).isEqualTo(12);
        assertThat(student.getMaxFileBytes()).isEqualTo(9L);
        assertThat(student.getDataDir()).isEqualTo("data");
        assertThat(student.getFilesDir()).isEqualTo("files");
        assertThat(student.isImportJson()).isFalse();

        GovtJobsProperties.Session session = props.getSession();
        assertThat(session.getSecret()).isEmpty();
        assertThat(session.getStudentTtlDays()).isEqualTo(14);
        assertThat(session.getOpsTtlHours()).isEqualTo(12);
        assertThat(session.isCookieSecure()).isFalse();
        session.setSecret("secret");
        session.setStudentTtlDays(2);
        session.setOpsTtlHours(3);
        session.setCookieSecure(true);
        assertThat(session.getSecret()).isEqualTo("secret");
        assertThat(session.getStudentTtlDays()).isEqualTo(2);
        assertThat(session.getOpsTtlHours()).isEqualTo(3);
        assertThat(session.isCookieSecure()).isTrue();

        GovtJobsProperties.Mail mail = props.getMail();
        assertThat(mail.getFrom()).contains("onboarding@resend.dev");
        assertThat(mail.getPublicSiteUrl()).isEqualTo("http://localhost:8090");
        assertThat(mail.getResendApiKey()).isEmpty();
        assertThat(mail.getResetTtlHours()).isEqualTo(1);
        mail.setFrom("Desk <desk@example.com>");
        mail.setPublicSiteUrl("http://localhost");
        mail.setResendApiKey("re_test");
        mail.setResetTtlHours(2);
        assertThat(mail.getFrom()).isEqualTo("Desk <desk@example.com>");
        assertThat(mail.getPublicSiteUrl()).isEqualTo("http://localhost");
        assertThat(mail.getResendApiKey()).isEqualTo("re_test");
        assertThat(mail.getResetTtlHours()).isEqualTo(2);

        GovtJobsProperties.Catalog catalog = props.getCatalog();
        assertThat(catalog.getPageSize()).isEqualTo(12);
        assertThat(catalog.getSource()).isEqualTo("json");
        assertThat(catalog.isSeedDummy()).isFalse();
        catalog.setPageSize(5);
        catalog.setSource("postgres");
        catalog.setSeedDummy(true);
        assertThat(catalog.getPageSize()).isEqualTo(5);
        assertThat(catalog.getSource()).isEqualTo("postgres");
        assertThat(catalog.isSeedDummy()).isTrue();

        GovtJobsProperties.RateLimit rate = props.getRateLimit();
        assertThat(rate.getWindowMinutes()).isEqualTo(15);
        assertThat(rate.getLoginMax()).isEqualTo(5);
        assertThat(rate.getCollectMax()).isEqualTo(20);
        rate.setWindowMinutes(1);
        rate.setLoginMax(9);
        rate.setCollectMax(8);
        assertThat(rate.getWindowMinutes()).isEqualTo(1);
        assertThat(rate.getLoginMax()).isEqualTo(9);
        assertThat(rate.getCollectMax()).isEqualTo(8);
    }
}
