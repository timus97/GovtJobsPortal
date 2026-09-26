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
}
