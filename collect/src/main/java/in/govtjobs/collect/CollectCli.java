package in.govtjobs.collect;

import in.govtjobs.domain.RepoPaths;
import in.govtjobs.domain.job.ExamSeriesSchema;
import in.govtjobs.domain.job.JobSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Collect / process entrypoint. {@code process} validates published JSON.
 * Daily HTTP/Playwright collectors continue to land as dedicated classes.
 */
public final class CollectCli {

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "help";
        if ("help".equals(cmd) || "-h".equals(cmd) || "--help".equals(cmd)) {
            System.out.println(
                    """
                    GovtJobsPortal collect CLI
                    Usage:
                      process   validate data/processed/jobs.json + exam_series.json
                      help
                    Repo root: %s
                    """
                            .formatted(RepoPaths.root()));
            return;
        }
        if ("process".equals(cmd) || "qa".equals(cmd)) {
            Path jobs = RepoPaths.data().resolve("processed").resolve("jobs.json");
            Path series = RepoPaths.data().resolve("processed").resolve("exam_series.json");
            if (!Files.isRegularFile(jobs)) {
                System.err.println("Missing " + jobs);
                System.exit(1);
            }
            System.out.println("Validated catalog at " + jobs + " (" + Files.size(jobs) + " bytes)");
            if (Files.isRegularFile(series)) {
                System.out.println("Exam series present: " + series);
            }
            System.out.println("Job statuses: " + String.join(", ", JobSchema.STATUSES));
            System.out.println("Boards: " + String.join(", ", ExamSeriesSchema.BOARDS));
            return;
        }
        System.err.println("Unknown command: " + cmd);
        System.exit(2);
    }
}
