package in.govtjobs.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReturnToTest {

    @Test
    void allowsAJobOrPreparePathOnly() {
        assertThat(ReturnTo.sanitize("/jobs/321c05e538ceea7a")).isEqualTo("/jobs/321c05e538ceea7a");
        assertThat(ReturnTo.sanitize("/prepare")).isEqualTo("/prepare");
        assertThat(ReturnTo.sanitize("/prepare?board=SSC")).isEqualTo("/prepare?board=SSC");
        assertThat(ReturnTo.sanitize("https://evil.example/jobs/a")).isNull();
        assertThat(ReturnTo.sanitize("//evil.example")).isNull();
        assertThat(ReturnTo.sanitize("/ops")).isNull();
        assertThat(ReturnTo.sanitize("/jobs/a/b")).isNull();
        assertThat(ReturnTo.sanitize(null)).isNull();
    }
}
