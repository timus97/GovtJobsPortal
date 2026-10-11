package in.govtjobs.collect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

/** Compact data/processed/collect-progress.json. Findings stop at 250 and the source log at 50. */
final class CollectProgress {

    private static final int FINDINGS_CAP = 250;
    private static final int SOURCE_LOG_CAP = 50;

    private final Path file;
    private final String runId;
    private final String startedAt;
    private final List<Map<String, Object>> findings = new ArrayList<>();
    private final List<Map<String, Object>> sourcesLog = new ArrayList<>();
    private int total;
    private int done;
    private int ok;
    private int failed;
    private int zero;
    private int records;
    private String current;
    private boolean running;

    CollectProgress(Path root, String runId, String startedAt) {
        this.file = root.resolve("data").resolve("processed").resolve("collect-progress.json");
        this.runId = runId;
        this.startedAt = startedAt;
    }

    synchronized void start(int sources) {
        this.total = sources;
        this.running = true;
        write();
    }

    synchronized void current(String sourceId) {
        this.current = sourceId;
        write();
    }

    synchronized void finished(CollectOrchestrator.SourceOutcome outcome) {
        done++;
        records += Math.max(0, outcome.records());
        if (outcome.ok()) {
            ok++;
        } else if (outcome.kind() == SourceRunKind.EMPTY) {
            zero++;
        } else {
            failed++;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceId", outcome.sourceId());
        row.put("outcome", outcome.kind() == null ? "failed" : outcome.kind().label());
        row.put("records", outcome.records());
        row.put("message", outcome.message());
        sourcesLog.add(row);
        while (sourcesLog.size() > SOURCE_LOG_CAP) {
            sourcesLog.remove(0);
        }
        for (String error : outcome.errors()) {
            if (findings.size() >= FINDINGS_CAP) {
                break;
            }
            Map<String, Object> finding = new LinkedHashMap<>();
            finding.put("sourceId", outcome.sourceId());
            finding.put("reason", error);
            findings.add(finding);
        }
        current = null;
        write();
    }

    synchronized void finish() {
        running = false;
        current = null;
        write();
    }

    private void write() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("running", running);
        report.put("runId", runId);
        report.put("startedAt", startedAt);
        report.put("finishedAt", running ? null : Instant.now().toString());
        report.put("sourcesTotal", total);
        report.put("sourcesDone", done);
        report.put("sourcesOk", ok);
        report.put("sourcesFailed", failed);
        report.put("sourcesZero", zero);
        report.put("recordsWritten", records);
        report.put("current", current);
        report.put("findings", List.copyOf(findings));
        report.put("sourcesLog", List.copyOf(sourcesLog));
        try {
            BuildJobs.writeAtomic(file, report);
        } catch (Exception ignored) {
            // Progress is a desk aid. The collect report is the record of the run.
        }
    }
}
