package in.govtjobs.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeatureFlagsTest {

    @Test
    void defaultsAreOnAndSettersRoundTrip() {
        FeatureFlags flags = new FeatureFlags();
        assertThat(flags.isStudent()).isTrue();
        assertThat(flags.isServerMatch()).isTrue();
        assertThat(flags.isPrepare()).isTrue();
        assertThat(flags.isUnpublish()).isTrue();

        flags.setStudent(false);
        flags.setServerMatch(false);
        flags.setPrepare(false);
        flags.setUnpublish(false);
        assertThat(flags.isStudent()).isFalse();
        assertThat(flags.isServerMatch()).isFalse();
        assertThat(flags.isPrepare()).isFalse();
        assertThat(flags.isUnpublish()).isFalse();
    }
}
