package in.govtjobs.web.mail;

import in.govtjobs.web.config.GovtJobsProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class StudentMailService {

    private static final Logger log = LoggerFactory.getLogger(StudentMailService.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final GovtJobsProperties props;
    private final JavaMailSender mailSender;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public StudentMailService(GovtJobsProperties props, ObjectProvider<JavaMailSender> mailSender) {
        this.props = props;
        this.mailSender = mailSender.getIfAvailable();
    }

    public String provider() {
        if (resendKey() != null) {
            return "resend";
        }
        if (smtpReady()) {
            return "smtp";
        }
        return "dev";
    }

    public boolean allowDevLink() {
        return !props.getSession().isCookieSecure() && "dev".equals(provider());
    }

    public SendResult sendPasswordReset(String to, String url, String expiresAt) {
        Bodies bodies = bodies(url, expiresAt);
        try {
            if (resendKey() != null) {
                sendResend(to, bodies);
                return new SendResult(true, "resend", null);
            }
            if (smtpReady()) {
                sendSmtp(to, bodies);
                return new SendResult(true, "smtp", null);
            }
            log.info("mail.dev_reset issued provider=dev (link shown only on local host)");
            return new SendResult(false, "dev", "no_mailer");
        } catch (Exception e) {
            log.warn("mail.reset_failed err={}", e.toString());
            return new SendResult(false, provider(), "send_failed");
        }
    }

    private void sendSmtp(String to, Bodies bodies) throws Exception {
        var message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(props.getMail().getFrom());
        helper.setTo(to);
        helper.setSubject(bodies.subject);
        helper.setText(bodies.text, bodies.html);
        mailSender.send(message);
    }

    private void sendResend(String to, Bodies bodies) throws Exception {
        String json =
                """
                {"from":%s,"to":[%s],"subject":%s,"text":%s,"html":%s}
                """
                        .formatted(
                                jsonString(props.getMail().getFrom()),
                                jsonString(to),
                                jsonString(bodies.subject),
                                jsonString(bodies.text),
                                jsonString(bodies.html));
        HttpRequest request = HttpRequest.newBuilder(URI.create(RESEND_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + resendKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("Resend " + response.statusCode());
        }
    }

    private boolean smtpReady() {
        String host = props.getMail().getSmtpHost();
        return mailSender != null && host != null && !host.isBlank();
    }

    private String resendKey() {
        String key = props.getMail().getResendApiKey();
        return key == null || key.isBlank() ? null : key;
    }

    private static Bodies bodies(String url, String expiresAt) {
        String when = expiresAt == null ? " It expires in one hour." : " It expires at " + expiresAt + ".";
        String text = String.join(
                "\n\n",
                "Reset your Sarkari Desk password using this one-time link:",
                url,
                "This is not a board or PSU account." + when,
                "If you did not ask for a reset, ignore this email.");
        String safe = url.replace("&", "&amp;").replace("\"", "&quot;");
        String html = "<p>Reset your Sarkari Desk password using this one-time link:</p>"
                + "<p><a href=\"" + safe + "\">" + safe + "</a></p>"
                + "<p>This is not a board or PSU account." + when + "</p>"
                + "<p>If you did not ask for a reset, ignore this email.</p>";
        return new Bodies("Reset your Sarkari Desk password", text, html);
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    public record SendResult(boolean sent, String provider, String reason) {}

    private record Bodies(String subject, String text, String html) {}

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", provider());
        return out;
    }
}
