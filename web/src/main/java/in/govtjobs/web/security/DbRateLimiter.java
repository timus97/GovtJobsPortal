package in.govtjobs.web.security;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DbRateLimiter {

    private final JdbcTemplate jdbc;

    public DbRateLimiter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean allow(String key, int max, long windowMs, long now) {
        if (key == null || key.isBlank() || max <= 0 || windowMs <= 0) {
            return true;
        }
        Instant resetAt = Instant.ofEpochMilli(now + windowMs);
        jdbc.update(
                """
                INSERT INTO rate_limits (bucket_key, hit_count, reset_at)
                VALUES (?, 1, ?)
                ON CONFLICT (bucket_key) DO UPDATE SET
                  hit_count = CASE WHEN rate_limits.reset_at <= now() THEN 1 ELSE rate_limits.hit_count + 1 END,
                  reset_at = CASE WHEN rate_limits.reset_at <= now() THEN EXCLUDED.reset_at ELSE rate_limits.reset_at END
                """,
                key,
                Timestamp.from(resetAt));
        Integer count = jdbc.queryForObject("SELECT hit_count FROM rate_limits WHERE bucket_key = ?", Integer.class, key);
        return count != null && count <= max;
    }

    public long retryAfterSeconds(String key, long now) {
        try {
            Timestamp reset = jdbc.queryForObject(
                    "SELECT reset_at FROM rate_limits WHERE bucket_key = ?", Timestamp.class, key);
            if (reset == null) {
                return 1L;
            }
            long ms = reset.getTime() - now;
            return Math.max(1L, (ms + 999L) / 1000L);
        } catch (EmptyResultDataAccessException e) {
            return 1L;
        }
    }
}
