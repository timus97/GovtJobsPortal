package in.govtjobs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepoPathsTest {

    @Test
    void rootContainsDataDirectory() {
        assertThat(RepoPaths.data()).exists().isDirectory();
        assertThat(RepoPaths.root().resolve("pom.xml")).exists();
    }
}
