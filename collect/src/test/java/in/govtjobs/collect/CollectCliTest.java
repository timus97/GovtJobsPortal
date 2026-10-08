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
    void helpAndUnknown() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            assertThat(CollectCli.run(new String[0])).isZero();
            assertThat(CollectCli.run(new String[] {"--help"})).isZero();
            assertThat(out.toString(StandardCharsets.UTF_8)).contains("process").contains("approve");
            assertThat(CollectCli.run(new String[] {"source"})).isEqualTo(2);
            assertThat(CollectCli.run(new String[] {"approve", "only-id"})).isEqualTo(2);
            assertThat(CollectCli.run(new String[] {"nope"})).isEqualTo(2);
            CollectCli.main(new String[] {"help"});
        } finally {
            System.setOut(previous);
        }
    }

    @Test
    void processWritesTheCatalogFiles(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        String previous = System.getProperty("user.dir");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream previousOut = System.out;
        System.setProperty("user.dir", root.toString());
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            assertThat(CollectCli.run(new String[] {"process"})).isZero();
            assertThat(CollectCli.run(new String[] {"qa"})).isZero();
            String text = out.toString(StandardCharsets.UTF_8);
            assertThat(text).contains("Pipeline complete");
            assertThat(text).contains("data/processed/jobs.json");
            Path processed = root.resolve("data").resolve("processed");
            for (String name : new String[] {
                "jobs.json",
                "opportunities.json",
                "exam_series.json",
                "stats.json",
                "quarantine.json",
                "run-report.json"
            }) {
                assertThat(processed.resolve(name)).isRegularFile();
            }
            try (var stream = Files.list(processed)) {
                assertThat(stream.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList())
                        .isEmpty();
            }
        } finally {
            System.setOut(previousOut);
            System.setProperty("user.dir", previous);
        }
    }

    @Test
    void corruptStagingDoesNotReplaceAnExistingCatalog(@TempDir Path root) throws Exception {
        Path processed = root.resolve("data").resolve("processed");
        Files.createDirectories(processed);
        Files.createDirectories(root.resolve("data").resolve("staging").resolve("bad"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(processed.resolve("jobs.json"), "STAY");
        Files.writeString(root.resolve("data").resolve("staging").resolve("bad").resolve("broken.json"), "{");
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", root.toString());
        try {
            assertThat(CollectCli.run(new String[] {"process"})).isEqualTo(1);
            assertThat(Files.readString(processed.resolve("jobs.json"))).isEqualTo("STAY");
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    @Test
    void approveRecordsTheReasonAndDoesNotPublish(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data").resolve("processed"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(
                root.resolve("data").resolve("processed").resolve("quarantine.json"),
                "[{\"reason\":\"needs_review\",\"job\":{\"id\":\"row-1\",\"title\":\"Clerk\"}}]");
        Files.writeString(root.resolve("data").resolve("processed").resolve("jobs.json"), "[]");
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", root.toString());
        try {
            assertThat(CollectCli.run(new String[] {"approve", "row-1", "operator", "confirmed"})).isZero();
            assertThat(Files.readString(root.resolve("data").resolve("seed").resolve("approvals.json")))
                    .contains("operator confirmed")
                    .contains("row-1");
            assertThat(Files.readString(root.resolve("data").resolve("processed").resolve("jobs.json")))
                    .isEqualTo("[]");
        } finally {
            System.setProperty("user.dir", previous);
        }
    }
}
