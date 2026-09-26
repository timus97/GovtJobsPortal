package in.govtjobs.web.security;

import in.govtjobs.web.store.OperatorStore;
import java.util.ArrayList;
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

public class OpsAuthProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(OpsAuthProvider.class);

    private final OperatorStore operators;

    public OpsAuthProvider(OperatorStore operators) {
        this.operators = operators;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String user = String.valueOf(authentication.getPrincipal());
        String pass = String.valueOf(authentication.getCredentials());
        Map<String, Object> row = operators.findByUsername(user);
        if (row == null || !operators.verify(user, pass)) {
            log.info("auth.ops_failed");
            throw new BadCredentialsException("Invalid operator credentials");
        }
        log.info("auth.ops_ok user={}", user);
        List<SimpleGrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_OPS"));
        if ("admin".equals(row.get("role"))) {
            roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return new UsernamePasswordAuthenticationToken(user, null, roles);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
