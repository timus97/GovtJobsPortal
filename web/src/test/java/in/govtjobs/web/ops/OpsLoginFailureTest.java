package in.govtjobs.web.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.govtjobs.web.config.FeatureFlags;
import in.govtjobs.web.security.SessionCookies;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.OperatorStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;

class OpsLoginFailureTest {

    @Test
    void wrongPasswordReturnsUnauthorizedAndKeepsTheForm() {
        OperatorStore operators = mock(OperatorStore.class);
        when(operators.verify("admin", "wrong-password")).thenReturn(false);
        when(operators.findByUsername("admin")).thenReturn(Map.of("username", "admin"));
        OpsController controller = new OpsController(
                mock(CollectQueue.class),
                mock(JobStore.class),
                operators,
                new OpsLog(),
                new FeatureFlags(),
                mock(SessionCookies.class));
        MockHttpServletResponse response = new MockHttpServletResponse();
        String view = controller.login("admin", "wrong-password", response, new ConcurrentModel());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(view).isEqualTo("auth/ops-login");
    }

    @Test
    void unknownUserIsAlsoUnauthorized() {
        OperatorStore operators = mock(OperatorStore.class);
        when(operators.verify("nobody", "wrong-password")).thenReturn(false);
        when(operators.findByUsername("nobody")).thenReturn(null);
        OpsController controller = new OpsController(
                mock(CollectQueue.class),
                mock(JobStore.class),
                operators,
                new OpsLog(),
                new FeatureFlags(),
                mock(SessionCookies.class));
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.login("nobody", "wrong-password", response, new ConcurrentModel());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
}
