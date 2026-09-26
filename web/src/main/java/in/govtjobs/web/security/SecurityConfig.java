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
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        http.securityMatcher("/ops/**")
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
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        http.csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(handler))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new SignedAuthFilter(tokens, cookies, students, operators, false),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/")))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers("/", "/health", "/css/**", "/js/**", "/favicon.svg", "/error")
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

    private static OncePerRequestFilter csrfCookieFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain filterChain)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (token != null) {
                    token.getToken();
                }
                filterChain.doFilter(request, response);
            }
        };
    }
}
