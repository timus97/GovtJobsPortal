package in.govtjobs.web.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.govtjobs.web.config.GovtJobsProperties;
import jakarta.mail.internet.MimeMessage;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class StudentMailServiceTest {

    @Test
    void devProviderShowsLinkOnlyWhenCookieIsNotSecure() {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getMail().setResendApiKey("  ");
        StudentMailService mail = service(props, null);
        assertThat(mail.provider()).isEqualTo("dev");
        assertThat(mail.allowDevLink()).isTrue();
        assertThat(mail.health()).containsEntry("provider", "dev");

        props.getSession().setCookieSecure(true);
        assertThat(mail.provider()).isEqualTo("dev");
        assertThat(mail.allowDevLink()).isFalse();
    }

    @Test
    void devSendBuildsBodiesAndDoesNotClaimAMailer() {
        GovtJobsProperties props = new GovtJobsProperties();
        StudentMailService mail = service(props, null);
        String url = "http://localhost/reset?t=1&x=\"a\\b\n";
        StudentMailService.SendResult open = mail.sendPasswordReset("a@b.co", url, null);
        assertThat(open.sent()).isFalse();
        assertThat(open.provider()).isEqualTo("dev");
        assertThat(open.reason()).isEqualTo("no_mailer");

        StudentMailService.SendResult dated = mail.sendPasswordReset("a@b.co", url, "2026-01-01T00:00:00Z");
        assertThat(dated.sent()).isFalse();
        assertThat(dated.reason()).isEqualTo("no_mailer");
    }

    @Test
    void resendProviderDoesNotAllowDevLinkAndSendUsesStubbedClient() throws Exception {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getMail().setResendApiKey("re_unit_test_not_real");
        props.getMail().setFrom("Desk \"x\" <a@b.co>\nline");
        props.getSession().setCookieSecure(false);
        StudentMailService mail = service(props, mock(JavaMailSender.class));
        assertThat(mail.provider()).isEqualTo("resend");
        assertThat(mail.allowDevLink()).isFalse();
        assertThat(mail.health().get("provider")).isEqualTo("resend");

        HttpResponse<String> ok = response(200);
        HttpResponse<String> bad = response(422);
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(ok)
                .thenReturn(bad)
                .thenThrow(new java.io.IOException("down"));
        swapHttp(mail, client);

        StudentMailService.SendResult sent =
                mail.sendPasswordReset("to@example.com", "https://example.com/r?a=1&b=\"c", "tomorrow");
        assertThat(sent.sent()).isTrue();
        assertThat(sent.provider()).isEqualTo("resend");
        assertThat(sent.reason()).isNull();

        StudentMailService.SendResult failed = mail.sendPasswordReset("to@example.com", "https://example.com", null);
        assertThat(failed.sent()).isFalse();
        assertThat(failed.provider()).isEqualTo("resend");
        assertThat(failed.reason()).isEqualTo("send_failed");

        StudentMailService.SendResult thrown = mail.sendPasswordReset("to@example.com", "https://example.com/reset", null);
        assertThat(thrown.reason()).isEqualTo("send_failed");
        verify(client, org.mockito.Mockito.atLeastOnce()).send(any(HttpRequest.class), any());

        props.getMail().setFrom(null);
        StudentMailService blankFrom = service(props, null);
        HttpClient blankClient = mock(HttpClient.class);
        when(blankClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);
        swapHttp(blankFrom, blankClient);
        assertThat(blankFrom.sendPasswordReset("to@example.com", "https://example.com/reset", null).sent()).isTrue();
    }

    @Test
    void smtpProviderSendsOnlyThroughTheInjectedMailer() throws Exception {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getMail().setResendApiKey(null);
        props.getMail().setSmtpHost("127.0.0.1");
        props.getSession().setCookieSecure(false);
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);
        StudentMailService mail = service(props, sender);
        assertThat(mail.provider()).isEqualTo("smtp");
        assertThat(mail.allowDevLink()).isFalse();
        StudentMailService.SendResult sent =
                mail.sendPasswordReset("to@example.com", "https://localhost/reset", "soon");
        assertThat(sent.sent()).isTrue();
        assertThat(sent.provider()).isEqualTo("smtp");
        verify(sender).send(message);

        when(sender.createMimeMessage()).thenThrow(new IllegalStateException("no session"));
        StudentMailService.SendResult failed = mail.sendPasswordReset("to@example.com", "https://localhost/reset", null);
        assertThat(failed.sent()).isFalse();
        assertThat(failed.reason()).isEqualTo("send_failed");
    }

    @Test
    void mailSenderWithoutSmtpHostStaysOnDev() {
        GovtJobsProperties props = new GovtJobsProperties();
        StudentMailService mail = service(props, null);
        assertThat(mail.provider()).isEqualTo("dev");
        assertThat(mail.health()).containsOnlyKeys("provider");
    }

    private static StudentMailService service(GovtJobsProperties props, JavaMailSender sender) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new StudentMailService(props, provider);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        return response;
    }

    private static void swapHttp(StudentMailService mail, HttpClient client) throws Exception {
        Field field = StudentMailService.class.getDeclaredField("http");
        field.setAccessible(true);
        field.set(mail, client);
    }
}
