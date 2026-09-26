package in.govtjobs.web.store;

import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.mail.StudentMailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final StudentStore students;
    private final StudentMailService mail;
    private final GovtJobsProperties props;

    public PasswordResetService(
            JdbcTemplate jdbc, StudentStore students, StudentMailService mail, GovtJobsProperties props) {
        this.jdbc = jdbc;
        this.students = students;
        this.mail = mail;
        this.props = props;
    }

    public Map<String, Object> requestReset(String email) {
        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put("ok", true);
        generic.put("message", "If that email is registered, we sent a reset link.");
        Map<String, Object> student = students.findInternalByEmail(email);
        if (student == null) {
            log.info("student.reset_unknown");
            return generic;
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = Instant.now();
        int hours = Math.max(1, props.getMail().getResetTtlHours());
        Instant expires = now.plus(hours, ChronoUnit.HOURS);
        jdbc.update("DELETE FROM password_reset_tokens WHERE student_id = ? AND used_at IS NULL", student.get("id"));
        jdbc.update(
                """
                INSERT INTO password_reset_tokens (id, token_hash, student_id, created_at, expires_at, used_at)
                VALUES (?,?,?,?,?,NULL)
                """,
                java.util.UUID.randomUUID().toString(),
                hash(token),
                student.get("id"),
                Timestamp.from(now),
                Timestamp.from(expires));
        String url = publicSite() + "/account/reset?token=" + token;
        StudentMailService.SendResult mailed =
                mail.sendPasswordReset(String.valueOf(student.get("email")), url, expires.toString());
        log.info("student.reset_requested id={} mailed={}", student.get("id"), mailed.sent());
        if (mail.allowDevLink()) {
            generic.put("devResetUrl", url);
            if (!mailed.sent()) {
                generic.put(
                        "devHint",
                        "No mail provider is configured (set RESEND_API_KEY or SMTP_HOST). Use the one-time link below.");
            }
        }
        return generic;
    }

    public boolean peek(String token) {
        return findLive(token) != null;
    }

    public Map<String, Object> consume(String rawToken, String password) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (token.isEmpty()) {
            throw new StoreException("VALIDATION", "Reset link is missing or invalid");
        }
        Map<String, Object> row = findLive(token);
        if (row == null) {
            throw new StoreException("EXPIRED", "This reset link is invalid or has expired");
        }
        Map<String, Object> student = students.setPassword(String.valueOf(row.get("studentId")), password);
        if (student == null) {
            throw new StoreException("EXPIRED", "This reset link is invalid or has expired");
        }
        jdbc.update(
                "UPDATE password_reset_tokens SET used_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                row.get("id"));
        log.info("student.reset_completed id={}", student.get("id"));
        return student;
    }

    private Map<String, Object> findLive(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        try {
            return jdbc.queryForObject(
                    """
                    SELECT id, student_id FROM password_reset_tokens
                    WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
                    """,
                    (rs, i) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("studentId", rs.getString("student_id"));
                        return row;
                    },
                    hash(rawToken.trim()));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private String publicSite() {
        String url = props.getMail().getPublicSiteUrl();
        if (url == null || url.isBlank()) {
            return "http://localhost:8090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
