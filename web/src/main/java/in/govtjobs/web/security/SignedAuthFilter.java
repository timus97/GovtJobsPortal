package in.govtjobs.web.security;

import in.govtjobs.web.store.OperatorStore;
import in.govtjobs.web.store.StudentStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SignedAuthFilter extends OncePerRequestFilter {

    private final SignedCookieService tokens;
    private final SessionCookies cookies;
    private final StudentStore students;
    private final OperatorStore operators;
    private final boolean opsChain;

    public SignedAuthFilter(
            SignedCookieService tokens,
            SessionCookies cookies,
            StudentStore students,
            OperatorStore operators,
            boolean opsChain) {
        this.tokens = tokens;
        this.cookies = cookies;
        this.students = students;
        this.operators = operators;
        this.opsChain = opsChain;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (opsChain) {
            SignedCookieService.Payload payload = tokens.parseOps(cookies.read(request, SignedCookieService.OPS_COOKIE));
            if (payload != null) {
                Map<String, Object> row = operators.findByUsername(payload.sub());
                if (row != null) {
                    List<SimpleGrantedAuthority> roles = new ArrayList<>();
                    roles.add(new SimpleGrantedAuthority("ROLE_OPS"));
                    if ("admin".equals(row.get("role")) || "admin".equals(payload.role())) {
                        roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(payload.sub(), null, roles);
                    auth.setDetails(payload.uid());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    cookies.refreshOps(response, payload);
                }
            }
        } else {
            SignedCookieService.Payload payload =
                    tokens.parseStudent(cookies.read(request, SignedCookieService.STUDENT_COOKIE));
            if (payload != null && students.findById(payload.uid()) != null) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        payload.sub(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
                auth.setDetails(payload.uid());
                SecurityContextHolder.getContext().setAuthentication(auth);
                cookies.refreshStudent(response, payload);
            }
        }
        chain.doFilter(request, response);
    }
}
