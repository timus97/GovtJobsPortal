package in.govtjobs.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.govtjobs.web.config.GovtJobsProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void nonLimitedRequestsPassAndLimitedPostsCanReturn429() throws Exception {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getRateLimit().setWindowMinutes(0);
        props.getRateLimit().setLoginMax(5);
        props.getRateLimit().setCollectMax(2);
        DbRateLimiter limiter = mock(DbRateLimiter.class);
        when(limiter.allow(anyString(), eq(5), eq(60_000L), anyLong())).thenReturn(true);
        when(limiter.allow(eq("/ops/collect|10.0.0.8"), eq(2), eq(60_000L), anyLong())).thenReturn(false);
        when(limiter.retryAfterSeconds(eq("/ops/collect|10.0.0.8"), anyLong())).thenReturn(9L);
        RateLimitFilter filter = new RateLimitFilter(props, limiter);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest get = request("GET", "/account/login", "127.0.0.1");
        filter.doFilter(get, new MockHttpServletResponse(), chain);

        MockHttpServletRequest other = request("POST", "/jobs", "127.0.0.1");
        filter.doFilter(other, new MockHttpServletResponse(), chain);

        for (String path : new String[] {"/account/login", "/account/register", "/account/forgot", "/account/reset", "/ops/login"}) {
            filter.doFilter(request("post", path, ""), new MockHttpServletResponse(), chain);
        }
        verify(limiter, org.mockito.Mockito.atLeast(5)).allow(anyString(), eq(5), eq(60_000L), anyLong());

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/ops/collect", "10.0.0.8"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("9");
        assertThat(blocked.getContentAsString()).contains("Too many attempts");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.argThat(req -> req instanceof MockHttpServletRequest r
                && "/ops/collect".equals(r.getRequestURI())), org.mockito.ArgumentMatchers.any());

        props.getRateLimit().setLoginMax(0);
        MockHttpServletResponse skipped = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/account/login", null), skipped, chain);
        assertThat(skipped.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        return request;
    }
}
