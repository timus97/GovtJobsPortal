package in.govtjobs.collect;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Daily job: fetch, process when a source returned notices, schema-check, alert, then snapshot.
 * A day with no notices leaves the catalog and jobs.prev.json alone and exits 0.
 * A schema failure or an alert transport failure does not snapshot and exits 1.
 */
public final class DailyPipeline {

    private DailyPipeline() {}

    public record Result(
            CollectOrchestrator.RunOutcome collect,
            boolean schemaOk,
            boolean alertOk,
            boolean snapshotted,
            int exitCode,
            String message,
            String deskMessage) {}

    public static Result run(Path root, SiteClient client) throws IOException {
        return run(root, client, CollectOrchestrator.Progress.NONE);
    }

    public static Result run(Path root, SiteClient client, CollectOrchestrator.Progress progress) throws IOException {
        return run(root, client, progress, DailyEnvironment.load(root), AlertMail.smtpMailer());
    }

    static Result run(
            Path root,
            SiteClient client,
            CollectOrchestrator.Progress progress,
            Map<String, String> env,
            AlertMail.Mailer mailer)
            throws IOException {
        CollectOrchestrator.RunOutcome outcome = CollectOrchestrator.daily(root, client, progress);
        return finish(root, outcome, env, mailer);
    }

    static Result finish(
            Path root, CollectOrchestrator.RunOutcome outcome, Map<String, String> env, AlertMail.Mailer mailer)
            throws IOException {
        if (outcome == null || !outcome.usable()) {
            return new Result(outcome, true, true, false, 0, "", "");
        }
        SchemaCheck.Report schema = SchemaCheck.check(root);
        if (!schema.ok()) {
            return new Result(
                    outcome,
                    false,
                    false,
                    false,
                    1,
                    schema.message(),
                    "Enabled sources were fetched and the student catalog was rebuilt. The schema check failed.");
        }
        AlertMail.Report alert = AlertMail.send(root, env, mailer);
        if (!alert.ok()) {
            return new Result(
                    outcome,
                    true,
                    false,
                    false,
                    1,
                    "Failed to send alert: " + alert.reason(),
                    "Enabled sources were fetched and the student catalog was rebuilt. The alert email failed.");
        }
        SnapshotPrev.copy(root);
        return new Result(outcome, true, true, true, 0, schema.message(), "");
    }
}
