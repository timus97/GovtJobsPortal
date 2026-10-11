package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DailyPipelineTest {

    private static final String OPEN_JOB = """
            [{"id":"job-1","title":"Junior Engineer","organization":"Demo Board","orgType":"central","selectionProcess":"cbt","hasExam":true,"officialUrl":"https://example.gov.in/advt","sourceId":"demo_board","sourceName":"Demo","sourceUrl":"https://example.gov.in/careers","status":"open"}]
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void schemaFailureDoesNotSnapshot(@TempDir Path root) throws Exception {
        Path processed = root.resolve("data/processed");
        Files.createDirectories(processed);
        Files.writeString(processed.resolve("jobs.json"), "[{\"title\":\"no id\"}]");
        DailyPipeline.Result result = DailyPipeline.finish(
                root, usable(), Map.of(), mail -> {
                    throw new AssertionError("mail");
                });
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.schemaOk()).isFalse();
        assertThat(result.snapshotted()).isFalse();
        assertThat(result.deskMessage()).contains("schema check failed");
        assertThat(result.message()).contains("Schema check failed");
        assertThat(Files.exists(processed.resolve("jobs.prev.json"))).isFalse();
        assertThat(Files.exists(processed.resolve("alert-report.json"))).isFalse();
    }

    @Test
    void disabledAlertWritesTheReportAndSnapshots(@TempDir Path root) throws Exception {
        writeJobs(root, OPEN_JOB);
        DailyPipeline.Result result = DailyPipeline.finish(root, usable(), Map.of("ALERT_ENABLED", "false"), mail -> {
            throw new AssertionError("mail");
        });
        assertThat(result.exitCode()).isZero();
        assertThat(result.snapshotted()).isTrue();
        JsonNode report = mapper.readTree(root.resolve("data/processed/alert-report.json").toFile());
        assertThat(report.path("sent").asBoolean()).isFalse();
        assertThat(report.path("reason").asText()).isEqualTo("ALERT_ENABLED not true");
        assertThat(Files.readAllBytes(root.resolve("data/processed/jobs.prev.json")))
                .isEqualTo(Files.readAllBytes(root.resolve("data/processed/jobs.json")));
    }

    @Test
    void missingMailboxAndFirstRunAndQuietDayStillSnapshot(@TempDir Path root) throws Exception {
        writeJobs(root, OPEN_JOB);
        DailyPipeline.Result missing = DailyPipeline.finish(
                root, usable(), Map.of("ALERT_ENABLED", "true"), mail -> {
                    throw new AssertionError("mail");
                });
        assertThat(missing.exitCode()).isZero();
        assertThat(missing.snapshotted()).isTrue();
        assertThat(mapper.readTree(root.resolve("data/processed/alert-report.json").toFile()).path("reason").asText())
                .isEqualTo("Missing SMTP_HOST or ALERT_TO");

        Files.delete(root.resolve("data/processed/jobs.prev.json"));
        Map<String, String> ready = mailbox();
        DailyPipeline.Result first = DailyPipeline.finish(root, usable(), ready, mail -> {
            throw new AssertionError("mail");
        });
        assertThat(first.exitCode()).isZero();
        assertThat(mapper.readTree(root.resolve("data/processed/alert-report.json").toFile()).path("reason").asText())
                .contains("No jobs.prev.json");

        Files.writeString(root.resolve("data/processed/jobs.prev.json"), OPEN_JOB);
        List<AlertMail.Mail> sent = new ArrayList<>();
        DailyPipeline.Result quiet = DailyPipeline.finish(root, usable(), ready, sent::add);
        assertThat(quiet.exitCode()).isZero();
        assertThat(sent).isEmpty();
        assertThat(mapper.readTree(root.resolve("data/processed/alert-report.json").toFile()).path("reason").asText())
                .isEqualTo("no_new_jobs");
    }

    @Test
    void transportFailureSkipsTheSnapshotAndANewJobIsMailed(@TempDir Path root) throws Exception {
        writeJobs(root, OPEN_JOB);
        Files.writeString(root.resolve("data/processed/jobs.prev.json"), "[]");
        DailyPipeline.Result failed = DailyPipeline.finish(root, usable(), mailbox(), mail -> {
            throw new java.io.IOException("Connection refused");
        });
        assertThat(failed.exitCode()).isEqualTo(1);
        assertThat(failed.alertOk()).isFalse();
        assertThat(failed.snapshotted()).isFalse();
        assertThat(failed.deskMessage()).contains("alert email failed");
        assertThat(mapper.readTree(root.resolve("data/processed/alert-report.json").toFile()).path("reason").asText())
                .contains("Connection refused");
        assertThat(Files.readString(root.resolve("data/processed/jobs.prev.json"))).isEqualTo("[]");

        List<AlertMail.Mail> sent = new ArrayList<>();
        DailyPipeline.Result mailed = DailyPipeline.finish(root, usable(), mailbox(), sent::add);
        assertThat(mailed.exitCode()).isZero();
        assertThat(mailed.snapshotted()).isTrue();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).subject()).isEqualTo("[Sarkari Jobs] 1 new government/PSU job(s)");
        assertThat(sent.get(0).text()).contains("http://localhost:8090/jobs/job-1");
        JsonNode report = mapper.readTree(root.resolve("data/processed/alert-report.json").toFile());
        assertThat(report.path("sent").asBoolean()).isTrue();
        assertThat(report.path("newCount").asInt()).isEqualTo(1);
    }

    @Test
    void anEmptyDayLeavesTheCatalogAlone(@TempDir Path root) throws Exception {
        DailyPipeline.Result result = DailyPipeline.finish(
                root, new CollectOrchestrator.RunOutcome(false, List.of()), Map.of(), mail -> {
                    throw new AssertionError("mail");
                });
        assertThat(result.exitCode()).isZero();
        assertThat(result.deskMessage()).isEmpty();
        assertThat(result.snapshotted()).isFalse();
        assertThat(Files.exists(root.resolve("data/processed/jobs.prev.json"))).isFalse();
    }

    @Test
    void dotenvFillsOnlyUnsetKeysAndSmtpRefusesAClosedPort(@TempDir Path root) throws Exception {
        Files.writeString(
                root.resolve(".env"),
                """
                PIPELINE_PORT_MARKER=from-file
                # comment
                PIPELINE_PORT_QUOTED="mail.example"
                """);
        Map<String, String> env = DailyEnvironment.load(root);
        if (System.getenv("PIPELINE_PORT_MARKER") == null) {
            assertThat(env.get("PIPELINE_PORT_MARKER")).isEqualTo("from-file");
        }
        if (System.getenv("PIPELINE_PORT_QUOTED") == null) {
            assertThat(env.get("PIPELINE_PORT_QUOTED")).isEqualTo("mail.example");
        }
        AlertMail.Mail mail = new AlertMail.Mail(
                "127.0.0.1",
                1,
                false,
                null,
                null,
                "bot@example.com",
                "ops@example.com",
                "subject",
                "text",
                "<p>text</p>");
        assertThatThrownBy(() -> AlertMail.smtpMailer().send(mail)).isInstanceOf(Exception.class);
    }

    private static CollectOrchestrator.RunOutcome usable() {
        return new CollectOrchestrator.RunOutcome(true, List.of());
    }

    private static Map<String, String> mailbox() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("ALERT_ENABLED", "true");
        env.put("SMTP_HOST", "127.0.0.1");
        env.put("SMTP_PORT", "2525");
        env.put("ALERT_TO", "ops@example.com");
        env.put("SMTP_FROM", "bot@example.com");
        return env;
    }

    private static void writeJobs(Path root, String json) throws Exception {
        Path processed = root.resolve("data/processed");
        Files.createDirectories(processed);
        Files.writeString(processed.resolve("jobs.json"), json);
    }
}
