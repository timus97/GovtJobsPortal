package in.govtjobs.web.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SessionCookies {

    private final SignedCookieService tokens;

    public SessionCookies(SignedCookieService tokens) {
        this.tokens = tokens;
    }

    public void setStudent(HttpServletResponse response, String studentId, String email, long sessionEpoch) {
        write(
                response,
                SignedCookieService.STUDENT_COOKIE,
                tokens.signStudent(studentId, email, sessionEpoch),
                tokens.studentTtl());
        clear(response, SignedCookieService.OPS_COOKIE);
    }

    public void setOps(HttpServletResponse response, String operatorId, String username, String role) {
        write(response, SignedCookieService.OPS_COOKIE, tokens.signOps(operatorId, username, role), tokens.opsTtl());
        clear(response, SignedCookieService.STUDENT_COOKIE);
    }

    public void clearStudent(HttpServletResponse response) {
        clear(response, SignedCookieService.STUDENT_COOKIE);
    }

    public void clearOps(HttpServletResponse response) {
        clear(response, SignedCookieService.OPS_COOKIE);
    }

    public void refreshStudent(HttpServletResponse response, SignedCookieService.Payload payload) {
        if (payload == null) {
            return;
        }
        write(
                response,
                SignedCookieService.STUDENT_COOKIE,
                tokens.signStudent(payload.uid(), payload.sub(), payload.epoch()),
                tokens.studentTtl());
    }

    public void refreshOps(HttpServletResponse response, SignedCookieService.Payload payload) {
        if (payload == null) {
            return;
        }
        write(
                response,
                SignedCookieService.OPS_COOKIE,
                tokens.signOps(payload.uid(), payload.sub(), payload.role()),
                tokens.opsTtl());
    }

    public String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void write(HttpServletResponse response, String name, String value, Duration ttl) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(tokens.cookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge((int) Math.min(Integer.MAX_VALUE, Math.max(0, ttl.toSeconds())));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private void clear(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(tokens.cookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
