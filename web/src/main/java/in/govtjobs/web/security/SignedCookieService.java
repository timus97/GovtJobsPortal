package in.govtjobs.web.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SignedCookieService {

    private static final Logger log = LoggerFactory.getLogger(SignedCookieService.class);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String STUDENT_AUD = "student";
    private static final String OPS_AUD = "ops";

    public static final String STUDENT_COOKIE = "student_session";
    public static final String OPS_COOKIE = "ops_session";

    private final ObjectMapper mapper;
    private final GovtJobsProperties props;
    private volatile byte[] secret;

    public SignedCookieService(ObjectMapper mapper, GovtJobsProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    @PostConstruct
    void init() {
        String configured = props.getSession().getSecret();
        if (configured != null && !configured.isBlank()) {
            secret = configured.getBytes(StandardCharsets.UTF_8);
            return;
        }
        if (props.getSession().isCookieSecure()) {
            throw new IllegalStateException("SESSION_SECRET is required when COOKIE_SECURE=true");
        }
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        secret = HexFormat.of().formatHex(generated).getBytes(StandardCharsets.UTF_8);
        log.warn("SESSION_SECRET not set; using ephemeral secret (sessions reset on restart)");
    }

    public Duration studentTtl() {
        int days = Math.max(1, props.getSession().getStudentTtlDays());
        return Duration.ofDays(days);
    }

    public Duration opsTtl() {
        int hours = Math.max(1, props.getSession().getOpsTtlHours());
        return Duration.ofHours(hours);
    }

    public boolean cookieSecure() {
        return props.getSession().isCookieSecure();
    }

    public String signStudent(String studentId, String email, long sessionEpoch) {
        return sign(STUDENT_AUD, studentId, email, "student", studentTtl(), sessionEpoch);
    }

    public String signOps(String operatorId, String username, String role) {
        return sign(OPS_AUD, operatorId, username, role == null ? "operator" : role, opsTtl(), 0);
    }

    public Payload parseStudent(String token) {
        return parse(token, STUDENT_AUD);
    }

    public Payload parseOps(String token) {
        return parse(token, OPS_AUD);
    }

    String sign(String aud, String uid, String sub, String role, Duration ttl, long epoch) {
        try {
            long now = System.currentTimeMillis() / 1000L;
            Payload payload = new Payload(1, aud, uid, sub, role, now, now + Math.max(60, ttl.toSeconds()), epoch);
            String body = B64.encodeToString(mapper.writeValueAsBytes(payload));
            return body + "." + B64.encodeToString(hmac(body));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign session cookie", e);
        }
    }

    Payload parse(String token, String expectedAud) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String body = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        try {
            byte[] expected = hmac(body);
            byte[] given = B64D.decode(sig);
            if (!MessageDigest.isEqual(expected, given)) {
                return null;
            }
            Payload payload = mapper.readValue(B64D.decode(body), Payload.class);
            if (payload == null
                    || payload.v != 1
                    || payload.uid == null
                    || payload.uid.isBlank()
                    || payload.exp <= System.currentTimeMillis() / 1000L) {
                return null;
            }
            if (expectedAud != null && !expectedAud.equals(payload.aud)) {
                return null;
            }
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Payload(int v, String aud, String uid, String sub, String role, long iat, long exp, long epoch) {}
}
