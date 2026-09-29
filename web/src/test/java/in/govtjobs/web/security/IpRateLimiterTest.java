package in.govtjobs.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpRateLimiterTest {

    @Test
    void fifthAttemptInWindowIsAllowedSixthIsNot() {
        IpRateLimiter limiter = new IpRateLimiter();
        long now = 1_000_000L;
        for (int i = 1; i <= 5; i++) {
            assertThat(limiter.allow("127.0.0.1|/account/login", 5, 15 * 60_000L, now)).isTrue();
        }
        assertThat(limiter.allow("127.0.0.1|/account/login", 5, 15 * 60_000L, now)).isFalse();
        assertThat(limiter.allow("127.0.0.1|/account/login", 5, 15 * 60_000L, now + 16 * 60_000L)).isTrue();
    }

    @Test
    void blankKeysAndNonPositiveWindowsAreAlwaysAllowed() {
        IpRateLimiter limiter = new IpRateLimiter();
        assertThat(limiter.allow(null, 5, 1_000L, 1L)).isTrue();
        assertThat(limiter.allow("  ", 5, 1_000L, 1L)).isTrue();
        assertThat(limiter.allow("k", 0, 1_000L, 1L)).isTrue();
        assertThat(limiter.allow("k", 5, 0L, 1L)).isTrue();
        assertThat(limiter.retryAfterSeconds("missing", 1L)).isEqualTo(1L);
    }

    @Test
    void retryAfterAndPruneDropExpiredBuckets() {
        IpRateLimiter limiter = new IpRateLimiter();
        assertThat(limiter.allow("live", 5, 10_000L, 1_000L)).isTrue();
        assertThat(limiter.retryAfterSeconds("live", 1_000L)).isGreaterThan(1L);
        assertThat(limiter.retryAfterSeconds("live", 20_000L)).isEqualTo(1L);

        for (int i = 0; i < 500; i++) {
            assertThat(limiter.allow("old-" + i, 5, 1_000L, 1L)).isTrue();
        }
        assertThat(limiter.allow("fresh", 5, 5_000L, 10_000L)).isTrue();
        assertThat(limiter.retryAfterSeconds("old-0", 10_000L)).isEqualTo(1L);
        assertThat(limiter.retryAfterSeconds("fresh", 10_000L)).isGreaterThan(1L);
    }
}
