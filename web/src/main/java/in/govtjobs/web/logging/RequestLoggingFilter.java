package in.govtjobs.web.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = sanitizeRequestId(request.getHeader("X-Request-Id"));
        MDC.put("requestId", requestId);
        long started = System.nanoTime();
        try {
            response.setHeader("X-Request-Id", requestId);
            chain.doFilter(request, response);
        } finally {
            if (shouldLog(request.getRequestURI())) {
                long ms = (System.nanoTime() - started) / 1_000_000L;
                log.info(
                        "http method={} path={} status={} ms={} actor={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        ms,
                        actor());
            }
            MDC.clear();
        }
    }

    private static String sanitizeRequestId(String incoming) {
        if (incoming != null) {
            String trimmed = incoming.trim();
            if (trimmed.matches("[A-Za-z0-9-]{1,64}")) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String actor() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "-";
        }
        boolean student = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()));
        if (student) {
            Object details = auth.getDetails();
            if (details instanceof String id && !id.isBlank() && !id.contains("@")) {
                return id;
            }
            return "-";
        }
        String name = auth.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? "-" : name;
    }

    private static boolean shouldLog(String path) {
        return path != null
                && !path.startsWith("/css/")
                && !path.startsWith("/js/")
                && !path.equals("/favicon.svg");
    }
}
