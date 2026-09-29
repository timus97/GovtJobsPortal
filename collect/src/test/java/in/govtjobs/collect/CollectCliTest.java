package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectCliTest {

    @Test
    void helpAndProcessAndUnknown() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            assertThat(CollectCli.run(new String[0])).isZero();
            assertThat(CollectCli.run(new String[] {"--help"})).isZero();
            assertThat(out.toString(StandardCharsets.UTF_8)).contains("process");
            assertThat(CollectCli.run(new String[] {"process"})).isZero();
            assertThat(CollectCli.run(new String[] {"qa"})).isZero();
            assertThat(CollectCli.run(new String[] {"nope"})).isEqualTo(2);
            CollectCli.main(new String[] {"help"});
        } finally {
            System.setOut(previous);
        }
    }

    @Test
    void processReportsAMissingCatalog(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", root.toString());
        try {
            assertThat(CollectCli.run(new String[] {"process"})).isEqualTo(1);
        } finally {
            System.setProperty("user.dir", previous);
        }
    }
}
