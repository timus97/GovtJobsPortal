package in.govtjobs.collect;

import in.govtjobs.domain.RepoPaths;

/**
 * Collect / process entrypoint.
 * {@code process} builds the catalog JSON. {@code daily} and {@code source} fetch.
 * {@code approve} records a quarantine decision for the next process.
 */
public final class CollectCli {

    public static void main(String[] args) throws Exception {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "help";
        if ("help".equals(cmd) || "-h".equals(cmd) || "--help".equals(cmd)) {
            System.out.println(
                    """
                    GovtJobsPortal collect CLI
                    Usage:
                      daily                  fetch enabled sources, then process if one succeeded
                      source <id>            fetch one source and write its staging file
                      approve <id> <reason>  keep a quarantine row for the next process
                      process                build jobs, series, and quarantine JSON from seed + staging
                      qa                     same as process
                      help
                    Repo root: %s
                    """
                            .formatted(RepoPaths.root()));
            return 0;
        }
        if ("daily".equals(cmd) || "source".equals(cmd) || "approve".equals(cmd)) {
            try {
                return collect(cmd, args);
            } catch (Exception e) {
                String message = e.getMessage();
                System.err.println(message == null || message.isBlank() ? e.toString() : message);
                return 1;
            }
        }
        if ("process".equals(cmd) || "qa".equals(cmd)) {
            try {
                BuildJobs.RunSummary summary =
                        BuildJobs.run(RepoPaths.root(), BuildJobs.Options.from(args));
                System.out.println("Pipeline complete");
                System.out.println(summary.reportJson());
                System.out.println(
                        "Wrote " + summary.published() + " jobs → data/processed/jobs.json");
                System.out.println(
                        "Wrote " + summary.examSeries() + " exam series → data/processed/exam_series.json");
                return 0;
            } catch (Exception e) {
                String message = e.getMessage();
                System.err.println(message == null || message.isBlank() ? e.toString() : message);
                return 1;
            }
        }
        System.err.println("Unknown command: " + cmd);
        return 2;
    }

    private static int collect(String cmd, String[] args) throws Exception {
        if ("daily".equals(cmd)) {
            CollectOrchestrator.RunOutcome outcome = CollectOrchestrator.daily(RepoPaths.root(), new LiveSiteClient());
            System.out.println(outcome.usable() ? "Daily collect usable" : "Daily collect left the previous catalog");
            return 0;
        }
        if ("source".equals(cmd)) {
            if (args.length < 2 || args[1].isBlank()) {
                System.err.println("Usage: source <id>");
                return 2;
            }
            CollectOrchestrator.RunOutcome outcome =
                    CollectOrchestrator.source(RepoPaths.root(), args[1], new LiveSiteClient());
            CollectOrchestrator.SourceOutcome row = outcome.sources().get(0);
            System.out.println(row.sourceId() + " " + (row.ok() ? "ok" : "error") + " records=" + row.records());
            if (row.message() != null) {
                System.out.println(row.message());
            }
            return row.ok() ? 0 : 1;
        }
        if (args.length < 3 || args[1].isBlank() || args[2].isBlank()) {
            System.err.println("Usage: approve <id> <reason>");
            return 2;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        QuarantineApproval.approve(RepoPaths.root(), args[1], reason);
        System.out.println("Approved " + args[1] + " for the next process");
        return 0;
    }
}
