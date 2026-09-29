package in.govtjobs.web.security;

import in.govtjobs.web.config.FeatureFlags;
import in.govtjobs.web.store.OperatorStore;
import in.govtjobs.web.store.StudentStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final StudentStore students;
    private final OperatorStore operators;
    private final FeatureFlags flags;
    private final SignedCookieService tokens;
    private final SessionCookies cookies;

    public SecurityConfig(
            StudentStore students,
            OperatorStore operators,
            FeatureFlags flags,
            SignedCookieService tokens,
            SessionCookies cookies) {
        this.students = students;
        this.operators = operators;
        this.flags = flags;
        this.tokens = tokens;
        this.cookies = cookies;
    }

    @Bean
    @Order(1)
    SecurityFilterChain opsChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = csrfRepo();
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        http.securityMatcher("/ops/**")
                .headers(SecurityConfig::securityHeaders)
                .csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(handler))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new SignedAuthFilter(tokens, cookies, students, operators, true),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/ops/login")))
                .authorizeHttpRequests(a -> a.requestMatchers("/ops/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/ops/operators")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .hasRole("OPS"))
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = csrfRepo();
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        http.headers(SecurityConfig::securityHeaders)
                .csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(handler))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new SignedAuthFilter(tokens, cookies, students, operators, false),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/")))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers("/", "/health", "/css/**", "/js/**", "/favicon.svg", "/favicon.ico", "/error")
                            .permitAll()
                            .requestMatchers("/jobs", "/jobs/**", "/prepare")
                            .permitAll();
                    if (flags.isStudent()) {
                        a.requestMatchers("/account/**")
                                .permitAll()
                                .requestMatchers("/dashboard", "/desk/**", "/profile", "/match")
                                .hasRole("STUDENT")
                                .anyRequest()
                                .hasRole("STUDENT");
                    } else {
                        a.requestMatchers("/account/**", "/dashboard", "/desk/**", "/profile", "/match")
                                .denyAll()
                                .anyRequest()
                                .denyAll();
                    }
                })
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());
        return http.build();
    }

    private static void securityHeaders(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity> headers) {
        headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data:; "
                                + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                + "font-src 'self' https://fonts.gstatic.com; "
                                + "script-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(f -> f.deny());
    }

    private CookieCsrfTokenRepository csrfRepo() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        boolean secure = tokens.cookieSecure();
        repo.setCookieCustomizer(builder -> builder.sameSite("Lax").secure(secure).httpOnly(false).path("/"));
        return repo;
    }

    private OncePerRequestFilter csrfCookieFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain filterChain)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                String value = token == null ? "" : token.getToken();
                HttpServletResponse wrapped = new jakarta.servlet.http.HttpServletResponseWrapper(response) {
                    private boolean keep(String header, String headerValue) {
                        if (!"Set-Cookie".equalsIgnoreCase(header) || headerValue == null) {
                            return true;
                        }
                        if (!headerValue.startsWith("XSRF-TOKEN=")) {
                            return true;
                        }
                        return !headerValue.startsWith("XSRF-TOKEN=;") && !headerValue.contains("Max-Age=0");
                    }

                    @Override
                    public void addHeader(String name, String headerValue) {
                        if (keep(name, headerValue)) {
                            super.addHeader(name, headerValue);
                        }
                    }

                    @Override
                    public void setHeader(String name, String headerValue) {
                        if (keep(name, headerValue)) {
                            super.setHeader(name, headerValue);
                        }
                    }
                };
                filterChain.doFilter(request, wrapped);
                if (!value.isBlank()) {
                    String secure = tokens.cookieSecure() ? "; Secure" : "";
                    response.addHeader(
                            "Set-Cookie",
                            "XSRF-TOKEN=" + value + "; Path=/; SameSite=Lax" + secure);
                }
            }
        };
    }
}
