package in.govtjobs.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MatchExceptionTest {

    @Test
    void carriesStatusAndJoinedErrors() {
        MatchException empty = new MatchException(List.of());
        assertThat(empty.getStatusCode()).isEqualTo(400);
        assertThat(empty.getMessage()).isEqualTo("invalid profile");
        assertThat(empty.getErrors()).isEmpty();

        MatchException listed = new MatchException(null);
        assertThat(listed.getErrors()).isEmpty();

        MatchException detailed = new MatchException("  ", List.of("dob required"));
        assertThat(detailed.getMessage()).isEqualTo("dob required");
        assertThat(detailed.getErrors()).containsExactly("dob required");

        MatchException custom = new MatchException("check the notice", null);
        assertThat(custom.getMessage()).isEqualTo("check the notice");
        assertThat(custom.getErrors()).isEmpty();
    }
}
