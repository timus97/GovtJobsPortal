package in.govtjobs.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * {@code collect daily} and {@code collect source}. The report is written before the first fetch.
 * One source failure does not stop the run. Process runs only when at least one source succeeded,
 * so a failed run leaves yesterday's jobs.json in place.
 */
public final class CollectOrchestrator {

    static final int TIMEOUT_SECONDS = 120;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    private CollectOrchestrator() {}

    public record SourceOutcome(
            String sourceId, boolean ok, boolean attempted, int records, String message, List<String> errors) {}

    public record RunOutcome(boolean usable, List<SourceOutcome> sources) {}

    /** Notified as each registry source starts and finishes. Safe to ignore. */
    public interface Progress {
        Progress NONE = new Progress() {};

        default void sourceStarted(String sourceId) {}

        default void sourceFinished(SourceOutcome outcome) {}
    }

    public static RunOutcome daily(Path root, SiteClient client) throws IOException {
        return daily(root, client, Progress.NONE);
    }

    public static RunOutcome daily(Path root, SiteClient client, Progress progress) throws IOException {
        return run(root, loadEnabled(root), client, true, progress);
    }

    public static RunOutcome source(Path root, String sourceId, SiteClient client) throws IOException {
        return source(root, sourceId, client, Progress.NONE);
    }

    public static RunOutcome source(Path root, String sourceId, SiteClient client, Progress progress)
            throws IOException {
        SourceSpec spec = find(root, sourceId);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown source: " + sourceId);
        }
        return run(root, List.of(spec), client, false, progress);
    }

    /** Every registry row, including paused ones. A daily fetch skips paused and manual rows. */
    public static List<SourceSpec> registry(Path root) throws IOException {
        List<SourceSpec> specs = new ArrayList<>();
        for (Map<String, Object> row : readSources(root)) {
            SourceSpec spec = SourceSpec.from(row);
            if (!spec.sourceId().isBlank()) {
                specs.add(spec);
            }
        }
        return specs;
    }

    private static RunOutcome run(
            Path root, List<SourceSpec> sources, SiteClient client, boolean processOnSuccess, Progress progress)
            throws IOException {
        Progress listener = progress == null ? Progress.NONE : progress;
        Path processedDir = root.resolve("data").resolve("processed");
        Files.createDirectories(processedDir);
        String startedAt = Instant.now().toString();
        writeReport(processedDir.resolve("collect-report.json"), startedAt, false, false, List.of());
        List<SourceOutcome> outcomes = fetchAll(root, sources, client, listener);
        boolean anyOk = outcomes.stream().anyMatch(outcome -> outcome.attempted() && outcome.ok());
        boolean didProcess = false;
        if (processOnSuccess && anyOk) {
            BuildJobs.run(root, new BuildJobs.Options(false));
            didProcess = true;
        }
        writeReport(processedDir.resolve("collect-report.json"), startedAt, anyOk && didProcess, didProcess, outcomes);
        return new RunOutcome(anyOk && didProcess, outcomes);
    }

    private static List<SourceOutcome> fetchAll(
            Path root, List<SourceSpec> sources, SiteClient client, Progress listener) {
        SourceOutcome[] slots = new SourceOutcome[sources.size()];
        int workers = Math.min(4, Math.max(1, sources.size()));
        ExecutorService pool = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "collect-source");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < sources.size(); i++) {
                int index = i;
                SourceSpec source = sources.get(i);
                tasks.add(pool.submit(() -> {
                    listener.sourceStarted(source.sourceId());
                    SourceOutcome outcome = safely(root, source, client);
                    slots[index] = outcome;
                    listener.sourceFinished(outcome);
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        } catch (java.util.concurrent.ExecutionException ex) {
            pool.shutdownNow();
        } finally {
            pool.shutdown();
        }
        List<SourceOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            SourceOutcome outcome = slots[i];
            if (outcome == null) {
                SourceSpec source = sources.get(i);
                outcomes.add(new SourceOutcome(source.sourceId(), false, false, 0, "not finished", List.of("not finished")));
            } else {
                outcomes.add(outcome);
            }
        }
        return outcomes;
    }

    private static SourceOutcome safely(Path root, SourceSpec source, SiteClient client) {
        try {
            return one(root, source, client);
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.toString() : ex.getMessage();
            return new SourceOutcome(source.sourceId(), false, true, 0, message, List.of(message));
        }
    }

    private static void pruneOlderSnapshots(Path dir, Path written) {
        try (var children = Files.list(dir)) {
            children.filter(path -> !path.getFileName().equals(written.getFileName()))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".json") || name.endsWith(".jsonl");
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // The newest file is the one process reads.
                        }
                    });
        } catch (IOException ignored) {
            // Leaving older snapshots is safe. Process reads the newest file.
        }
    }

    private static SourceOutcome one(Path root, SourceSpec source, SiteClient client) throws IOException {
        if (source.defence() || source.manual()) {
            return new SourceOutcome(source.sourceId(), false, true, 0, SourceRules.CAPTCHA, List.of(SourceRules.CAPTCHA));
        }
        SourceCollect.Result result = SourceCollect.collect(source, client);
        Path dir = root.resolve("data").resolve("staging").resolve(safe(source.sourceId()));
        Files.createDirectories(dir);
        Path written = dir.resolve(System.currentTimeMillis() + ".json");
        BuildJobs.writeAtomic(written, result.records());
        pruneOlderSnapshots(dir, written);
        return new SourceOutcome(
                source.sourceId(),
                result.ok(),
                true,
                result.records().size(),
                result.message(),
                result.errors());
    }

    private static void writeReport(
            Path file, String startedAt, boolean usable, boolean processed, List<SourceOutcome> outcomes)
            throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startedAt", startedAt);
        report.put("finishedAt", outcomes.isEmpty() && !usable ? null : Instant.now().toString());
        report.put("usable", usable);
        report.put("processed", processed);
        report.put("timeoutSeconds", TIMEOUT_SECONDS);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SourceOutcome outcome : outcomes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceId", outcome.sourceId());
            row.put("ok", outcome.ok());
            row.put("records", outcome.records());
            row.put("message", outcome.message());
            row.put("errors", outcome.errors());
            rows.add(row);
        }
        report.put("sources", rows);
        BuildJobs.writeAtomic(file, report);
    }

    private static List<SourceSpec> loadEnabled(Path root) throws IOException {
        return SourceSpec.enabled(readSources(root));
    }

    private static SourceSpec find(Path root, String sourceId) throws IOException {
        for (Map<String, Object> row : readSources(root)) {
            if (sourceId.equals(String.valueOf(row.get("sourceId")))) {
                return SourceSpec.from(row);
            }
        }
        return null;
    }

    private static List<Map<String, Object>> readSources(Path root) throws IOException {
        Path file = root.resolve("data").resolve("sources").resolve("registry.json");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        JsonNode node = MAPPER.readTree(file.toFile());
        if (node == null || !node.isObject() || !node.path("sources").isArray()) {
            return List.of();
        }
        List<Map<String, Object>> rows = MAPPER.convertValue(node.get("sources"), LIST);
        return rows == null ? List.of() : rows;
    }

    private static String safe(String sourceId) {
        String cleaned = sourceId.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "source" : cleaned;
    }
}
