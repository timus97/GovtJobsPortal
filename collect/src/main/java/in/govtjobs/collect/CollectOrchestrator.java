package in.govtjobs.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code collect daily} and {@code collect source}. The report is written before the first fetch.
 * One source failure does not stop the run. Process runs only when at least one source returned
 * notices. An empty or failed source leaves its previous staging snapshot in place.
 */
public final class CollectOrchestrator {

    static final int TIMEOUT_SECONDS = 120;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    private CollectOrchestrator() {}

    public record SourceOutcome(
            String sourceId,
            SourceRunKind kind,
            boolean attempted,
            int records,
            String message,
            List<String> errors) {
        public boolean ok() {
            return kind != null && kind.producedNotices();
        }
    }

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

    /** One source with a short timeout. Production daily keeps {@link #TIMEOUT_SECONDS}. */
    static RunOutcome sourceWithin(Path root, String sourceId, SiteClient client, Duration timeout) throws IOException {
        SourceSpec spec = find(root, sourceId);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown source: " + sourceId);
        }
        return run(root, List.of(spec), client, false, Progress.NONE, timeout, 200, 10);
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
        return run(
                root,
                sources,
                client,
                processOnSuccess,
                progress,
                Duration.ofSeconds(TIMEOUT_SECONDS),
                envInt("PDF_MAX_PER_RUN", 200),
                envInt("PDF_MAX_PER_SOURCE", 10));
    }

    private static RunOutcome run(
            Path root,
            List<SourceSpec> sources,
            SiteClient client,
            boolean processOnSuccess,
            Progress progress,
            Duration timeout,
            int runCap,
            int sourceCap)
            throws IOException {
        Progress listener = progress == null ? Progress.NONE : progress;
        Path processedDir = root.resolve("data").resolve("processed");
        Files.createDirectories(processedDir);
        String startedAt = Instant.now().toString();
        String runId = startedAt.replace(":", "");
        writeReport(processedDir.resolve("collect-report.json"), startedAt, false, false, List.of());
        List<SourceOutcome> outcomes =
                fetchAll(root, sources, client, listener, timeout, runCap, sourceCap, runId, startedAt);
        boolean anyOk = outcomes.stream().anyMatch(SourceOutcome::ok);
        boolean didProcess = false;
        if (processOnSuccess && anyOk) {
            BuildJobs.run(root, new BuildJobs.Options(false));
            didProcess = true;
        }
        writeReport(processedDir.resolve("collect-report.json"), startedAt, anyOk && didProcess, didProcess, outcomes);
        return new RunOutcome(anyOk && didProcess, outcomes);
    }

    private static int envInt(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * At most four sources fetch at once. Each one has its own timeout. Cancelling a source does
     * not stop a blocked HTTP read, so the abandoned flag is what skips the staging write.
     */
    private static List<SourceOutcome> fetchAll(
            Path root,
            List<SourceSpec> sources,
            SiteClient client,
            Progress listener,
            Duration timeout,
            int runCap,
            int sourceCap,
            String runId,
            String startedAt) {
        int count = sources.size();
        SourceOutcome[] slots = new SourceOutcome[count];
        AtomicBoolean[] reported = new AtomicBoolean[count];
        AtomicBoolean[] abandoned = new AtomicBoolean[count];
        AtomicBoolean[] released = new AtomicBoolean[count];
        for (int i = 0; i < count; i++) {
            reported[i] = new AtomicBoolean();
            abandoned[i] = new AtomicBoolean();
            released[i] = new AtomicBoolean();
        }
        AtomicInteger runLeft = new AtomicInteger(Math.max(0, runCap));
        CollectProgress progress = new CollectProgress(root, runId, startedAt);
        progress.start(count);
        int workers = Math.min(4, Math.max(1, count));
        Semaphore gate = new Semaphore(workers);
        ExecutorService pool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "collect-source");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                SourceSpec source = sources.get(i);
                tasks.add(pool.submit(() -> {
                    boolean acquired = false;
                    try {
                        gate.acquire();
                        acquired = true;
                        if (abandoned[index].get()) {
                            return;
                        }
                        listener.sourceStarted(source.sourceId());
                        progress.current(source.sourceId());
                        SiteClient archived = new RawArchive(client, root, source.sourceId(), runId);
                        PdfBudget budget = PdfBudget.shared(Math.max(0, sourceCap), runLeft);
                        SourceOutcome outcome = safely(root, source, archived, abandoned[index], budget);
                        if (abandoned[index].get() || Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        if (reported[index].compareAndSet(false, true)) {
                            slots[index] = outcome;
                            progress.finished(outcome);
                            listener.sourceFinished(outcome);
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        if (acquired && released[index].compareAndSet(false, true)) {
                            gate.release();
                        }
                    }
                }));
            }
            long millis = Math.max(1, timeout == null ? TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS) : timeout.toMillis());
            for (int i = 0; i < tasks.size(); i++) {
                Future<?> task = tasks.get(i);
                try {
                    task.get(millis, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ex) {
                    abandoned[i].set(true);
                    task.cancel(true);
                    if (reported[i].compareAndSet(false, true)) {
                        SourceOutcome timed = timeoutOutcome(sources.get(i));
                        slots[i] = timed;
                        progress.finished(timed);
                        listener.sourceFinished(timed);
                    }
                    if (released[i].compareAndSet(false, true)) {
                        gate.release();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (java.util.concurrent.ExecutionException ex) {
                    if (reported[i].compareAndSet(false, true)) {
                        SourceOutcome crashed = timeoutOutcome(sources.get(i));
                        slots[i] = crashed;
                        progress.finished(crashed);
                        listener.sourceFinished(crashed);
                    }
                }
            }
        } finally {
            pool.shutdownNow();
            progress.finish();
        }
        List<SourceOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            SourceOutcome outcome = slots[i];
            if (outcome == null) {
                SourceSpec source = sources.get(i);
                outcomes.add(new SourceOutcome(
                        source.sourceId(), SourceRunKind.NOT_FINISHED, false, 0, "not finished", List.of("not finished")));
            } else {
                outcomes.add(outcome);
            }
        }
        return outcomes;
    }

    private static SourceOutcome timeoutOutcome(SourceSpec source) {
        String message = "source worker crashed or timed out";
        return new SourceOutcome(source.sourceId(), SourceRunKind.FAILED, true, 0, message, List.of(message));
    }

    private static SourceOutcome safely(
            Path root, SourceSpec source, SiteClient client, AtomicBoolean abandoned, PdfBudget budget) {
        try {
            return one(root, source, client, abandoned, budget);
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.toString() : ex.getMessage();
            return new SourceOutcome(source.sourceId(), SourceRunKind.FAILED, true, 0, message, List.of(message));
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

    private static boolean stopped(AtomicBoolean abandoned) {
        return (abandoned != null && abandoned.get()) || Thread.currentThread().isInterrupted();
    }

    private static SourceOutcome one(
            Path root, SourceSpec source, SiteClient client, AtomicBoolean abandoned, PdfBudget budget)
            throws IOException {
        if (stopped(abandoned)) {
            return timeoutOutcome(source);
        }
        if (source.defence() || source.manual()) {
            return new SourceOutcome(
                    source.sourceId(), SourceRunKind.FAILED, true, 0, SourceRules.CAPTCHA, List.of(SourceRules.CAPTCHA));
        }
        SourceCollect.Result result = SourceCollect.collect(source, client, Instant.now().toString(), budget);
        if (stopped(abandoned)) {
            return timeoutOutcome(source);
        }
        if (result.kind() != SourceRunKind.COMPLETE && result.kind() != SourceRunKind.PARTIAL) {
            return fromResult(source.sourceId(), result);
        }
        if (stopped(abandoned)) {
            return timeoutOutcome(source);
        }
        Path dir = stagingDirectory(root, source.sourceId());
        Files.createDirectories(dir);
        Path written = dir.resolve(System.currentTimeMillis() + ".json");
        BuildJobs.writeAtomic(written, result.records());
        if (result.kind() == SourceRunKind.COMPLETE) {
            pruneOlderSnapshots(dir, written);
        } else {
            retainPreviousSnapshot(dir, written);
        }
        return fromResult(source.sourceId(), result);
    }

    /** Staging directory for one registry source. Paste files live under {@code ops_paste} and are not written here. */
    public static Path stagingDirectory(Path root, String sourceId) {
        return root.resolve("data").resolve("staging").resolve(safe(sourceId));
    }

    private static SourceOutcome fromResult(String sourceId, SourceCollect.Result result) {
        String message = result.message();
        if ((message == null || message.isBlank())
                && result.kind() == SourceRunKind.PARTIAL
                && !result.errors().isEmpty()) {
            message = result.errors().get(0);
        }
        return new SourceOutcome(sourceId, result.kind(), true, result.records().size(), message, result.errors());
    }

    /**
     * A partial yield replaces the catalog input, and one previous non-empty snapshot stays on disk.
     * Empty files are removed. A file that does not parse is left for a later complete run.
     */
    private static void retainPreviousSnapshot(Path dir, Path written) {
        List<Path> keepers = new ArrayList<>();
        try (var children = Files.list(dir)) {
            for (Path path : children.toList()) {
                if (path.getFileName().equals(written.getFileName())) {
                    continue;
                }
                String name = path.getFileName().toString();
                if (!name.endsWith(".json") && !name.endsWith(".jsonl")) {
                    continue;
                }
                try {
                    if (BuildJobs.snapshotRecords(path).isEmpty()) {
                        Files.deleteIfExists(path);
                    } else {
                        keepers.add(path);
                    }
                } catch (IOException | RuntimeException ignored) {
                    // An unreadable file stays until a complete run replaces the directory.
                }
            }
        } catch (IOException ignored) {
            return;
        }
        keepers.sort(Comparator.comparingLong(CollectOrchestrator::mtimeMillis)
                .reversed()
                .thenComparing(path -> path.getFileName().toString()));
        for (int i = 1; i < keepers.size(); i++) {
            try {
                Files.deleteIfExists(keepers.get(i));
            } catch (IOException ignored) {
                // The newest previous file is the recovery copy.
            }
        }
    }

    private static long mtimeMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
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
            row.put("outcome", outcome.kind().label());
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
