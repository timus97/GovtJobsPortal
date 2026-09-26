package in.govtjobs.web.security;

import in.govtjobs.web.store.StudentStore;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class StudentAuthProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(StudentAuthProvider.class);

    private final StudentStore students;

    public StudentAuthProvider(StudentStore students) {
        this.students = students;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = String.valueOf(authentication.getPrincipal());
        String pass = String.valueOf(authentication.getCredentials());
        Map<String, Object> student = students.verify(email, pass);
        if (student == null) {
            log.info("auth.student_failed");
            throw new BadCredentialsException("Invalid email or password");
        }
        log.info("auth.student_ok");
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                student.get("email"), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        token.setDetails(student.get("id"));
        return token;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
