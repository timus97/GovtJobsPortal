package in.govtjobs.web.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void keepsASafeRequestIdAndSkipsStaticPaths() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest css = new MockHttpServletRequest("GET", "/css/app.css");
        css.addHeader("X-Request-Id", "abc-DEF-1");
        MockHttpServletResponse cssResponse = new MockHttpServletResponse();
        filter.doFilter(css, cssResponse, chain);
        assertThat(cssResponse.getHeader("X-Request-Id")).isEqualTo("abc-DEF-1");

        MockHttpServletRequest js = new MockHttpServletRequest("GET", "/js/app.js");
        filter.doFilter(js, new MockHttpServletResponse(), chain);
        MockHttpServletRequest icon = new MockHttpServletRequest("GET", "/favicon.svg");
        filter.doFilter(icon, new MockHttpServletResponse(), chain);
        verify(chain, org.mockito.Mockito.times(3)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUnsafeIdsAndLogsActors() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse bad = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/jobs");
        request.addHeader("X-Request-Id", "has space");
        filter.doFilter(request, bad, chain);
        assertThat(bad.getHeader("X-Request-Id")).matches("[A-Za-z0-9-]{8}");

        MockHttpServletResponse blank = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/health"), blank, chain);
        assertThat(blank.getHeader("X-Request-Id")).hasSize(8);

        MockHttpServletRequest tooLong = new MockHttpServletRequest("POST", "/account/login");
        tooLong.addHeader("X-Request-Id", "a".repeat(65));
        filter.doFilter(tooLong, new MockHttpServletResponse(), chain);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("ignored", "n/a"));
        SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);
        filter.doFilter(new MockHttpServletRequest("GET", "/jobs"), new MockHttpServletResponse(), chain);

        UsernamePasswordAuthenticationToken student = new UsernamePasswordAuthenticationToken(
                "student@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        student.setDetails("stu-1");
        SecurityContextHolder.getContext().setAuthentication(student);
        filter.doFilter(new MockHttpServletRequest("GET", "/dashboard"), new MockHttpServletResponse(), chain);

        student.setDetails("student@example.com");
        filter.doFilter(new MockHttpServletRequest("GET", "/profile"), new MockHttpServletResponse(), chain);
        student.setDetails("  ");
        filter.doFilter(new MockHttpServletRequest("GET", "/match"), new MockHttpServletResponse(), chain);
        student.setDetails(1);
        filter.doFilter(new MockHttpServletRequest("GET", "/desk/1"), new MockHttpServletResponse(), chain);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "ops@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_OPS"))));
        filter.doFilter(new MockHttpServletRequest("GET", "/ops"), new MockHttpServletResponse(), chain);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "  ", "n/a", List.of(new SimpleGrantedAuthority("ROLE_OPS"))));
        filter.doFilter(new MockHttpServletRequest("GET", "/ops/queue"), new MockHttpServletResponse(), chain);

        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        filter.doFilter(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), chain);
        assertThat(MDC.get("requestId")).isNull();
    }
}
