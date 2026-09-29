package in.govtjobs.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class DbRateLimiterTest {

    @Test
    void allowShortCircuitsAndReadsTheStoredCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DbRateLimiter limiter = new DbRateLimiter(jdbc);
        long now = 1_000L;
        assertThat(limiter.allow(null, 5, 1_000L, now)).isTrue();
        assertThat(limiter.allow("  ", 5, 1_000L, now)).isTrue();
        assertThat(limiter.allow("k", 0, 1_000L, now)).isTrue();
        assertThat(limiter.allow("k", 5, 0L, now)).isTrue();
        verify(jdbc, never()).update(anyString(), any(), any());

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("login"))).thenReturn(2, 6, null);
        assertThat(limiter.allow("login", 5, 60_000L, now)).isTrue();
        assertThat(limiter.allow("login", 5, 60_000L, now)).isFalse();
        assertThat(limiter.allow("login", 5, 60_000L, now)).isFalse();
        verify(jdbc, org.mockito.Mockito.times(3)).update(anyString(), eq("login"), any(Timestamp.class));
    }

    @Test
    void retryAfterUsesResetTimestampOrOneSecond() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DbRateLimiter limiter = new DbRateLimiter(jdbc);
        long now = 5_000L;
        when(jdbc.queryForObject(anyString(), eq(Timestamp.class), eq("k")))
                .thenReturn(new Timestamp(now + 2_500L), null, new Timestamp(now - 5_000L))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThat(limiter.retryAfterSeconds("k", now)).isEqualTo(3L);
        assertThat(limiter.retryAfterSeconds("k", now)).isEqualTo(1L);
        assertThat(limiter.retryAfterSeconds("k", now)).isEqualTo(1L);
        assertThat(limiter.retryAfterSeconds("k", now)).isEqualTo(1L);
    }
}
