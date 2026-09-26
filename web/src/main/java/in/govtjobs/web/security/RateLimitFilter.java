package in.govtjobs.web.security;

import in.govtjobs.web.config.GovtJobsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final GovtJobsProperties props;
    private final DbRateLimiter limiter;

    public RateLimitFilter(GovtJobsProperties props, DbRateLimiter limiter) {
        this.props = props;
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        int max = maxFor(path);
        if (max <= 0) {
            chain.doFilter(request, response);
            return;
        }
        long now = System.currentTimeMillis();
        long windowMs = Math.max(1, props.getRateLimit().getWindowMinutes()) * 60_000L;
        String key = path + "|" + clientIp(request);
        if (!limiter.allow(key, max, windowMs, now)) {
            long retry = limiter.retryAfterSeconds(key, now);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retry));
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write("Too many attempts. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }

    private int maxFor(String path) {
        if ("/account/login".equals(path)
                || "/account/register".equals(path)
                || "/account/forgot".equals(path)
                || "/account/reset".equals(path)
                || "/ops/login".equals(path)) {
            return props.getRateLimit().getLoginMax();
        }
        if ("/ops/collect".equals(path)) {
            return props.getRateLimit().getCollectMax();
        }
        return 0;
    }

    private static String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}
