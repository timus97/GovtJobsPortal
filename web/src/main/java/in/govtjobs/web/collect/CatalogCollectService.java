package in.govtjobs.web.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.collect.BuildJobs;
import in.govtjobs.collect.CollectOrchestrator;
import in.govtjobs.collect.SourceSpec;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.web.store.CatalogStore;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * One Java collect command at a time. Daily and rebuild copy the processed JSON into the
 * student catalog when the build succeeds. A single source only writes its staging file.
 */
@Service
public class CatalogCollectService {

    public record Start(boolean started, String notice) {}

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final Set<String> OUTCOMES = Set.of("complete", "partial", "empty", "failed", "not_finished");

    private final CatalogCollectCommands commands;
    private final Runnable refreshCatalog;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService workers = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "catalog-collect");
        thread.setDaemon(true);
        return thread;
    });
    private final Object gate = new Object();

    private boolean running;
    private String command = "";
    private String sourceId = "";
    private String startedBy = "";
    private String startedAt = "";
    private String currentSource = "";
    private int completed;
    private int total;
    private String message = "";
    private String lastSummary = "";
    private String lastFinishedAt = "";

    @Autowired
    public CatalogCollectService(CatalogCollectCommands commands, CatalogStore catalog) {
        this(commands, catalog::syncFromGit);
    }

    CatalogCollectService(CatalogCollectCommands commands, Runnable refreshCatalog) {
        this.commands = commands;
        this.refreshCatalog = refreshCatalog;
    }

    public Start startDaily(String who) {
        try {
            int enabled = enabledCount();
            if (enabled == 0) {
                return new Start(false, "No enabled sources in the registry.");
            }
            return begin("daily", "", who, enabled, this::runDaily);
        } catch (IOException ex) {
            return new Start(false, messageOf(ex));
        }
    }

    public Start startSource(String who, String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isBlank()) {
            return new Start(false, "Choose a source.");
        }
        try {
            if (find(id) == null) {
                return new Start(false, "Unknown source.");
            }
            return begin("source", id, who, 1, () -> runSource(id));
        } catch (IOException ex) {
            return new Start(false, messageOf(ex));
        }
    }

    public Start startProcess(String who) {
        return begin("process", "", who, 1, this::runProcess);
    }

    public List<Map<String, Object>> sources() throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SourceSpec spec : commands.registry()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceId", spec.sourceId());
            row.put("name", spec.name());
            row.put("enabled", spec.enabled());
            row.put("method", spec.method());
            String label = spec.sourceId() + " — " + spec.name();
            if (!spec.enabled()) {
                label = label + " (paused)";
            }
            row.put("label", label);
            rows.add(row);
        }
        return rows;
    }

    public int enabledCount() throws IOException {
        int count = 0;
        for (SourceSpec spec : commands.registry()) {
            if (spec.enabled() && !spec.manual()) {
                count++;
            }
        }
        return count;
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        synchronized (gate) {
            out.put("running", running);
            out.put("command", command);
            out.put("sourceId", sourceId);
            out.put("startedBy", startedBy);
            out.put("startedAt", startedAt);
            out.put("currentSource", currentSource);
            out.put("completed", completed);
            out.put("total", total);
            out.put("message", message);
            out.put("lastSummary", lastSummary);
            out.put("lastFinishedAt", lastFinishedAt);
        }
        out.put("report", readReport());
        return out;
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    private Start begin(String nextCommand, String nextSource, String who, int nextTotal, Runnable work) {
        synchronized (gate) {
            if (running) {
                return new Start(false, "A fetch is already running.");
            }
            running = true;
            command = nextCommand;
            sourceId = nextSource;
            startedBy = who == null || who.isBlank() ? "ops" : who;
            startedAt = Instant.now().toString();
            currentSource = "";
            completed = 0;
            total = nextTotal;
            message = "";
        }
        workers.execute(() -> {
            try {
                work.run();
            } catch (RuntimeException ex) {
                remember(messageOf(ex));
            } finally {
                synchronized (gate) {
                    running = false;
                    currentSource = "";
                    lastFinishedAt = Instant.now().toString();
                }
            }
        });
        return new Start(true, "");
    }

    private void runDaily() {
        try {
            CollectOrchestrator.RunOutcome outcome = commands.daily(progress());
            String note = commands.dailyNote();
            if (outcome.usable()) {
                refreshCatalog.run();
                remember(note == null || note.isBlank()
                        ? "Enabled sources were fetched and the student catalog was rebuilt."
                        : note);
            } else {
                remember(note == null || note.isBlank()
                        ? "No source returned notices, so the student catalog was left as it was."
                        : note);
            }
        } catch (IOException ex) {
            remember(messageOf(ex));
        }
    }

    private void runSource(String id) {
        try {
            CollectOrchestrator.RunOutcome outcome = commands.source(id, progress());
            if (outcome.sources().isEmpty()) {
                remember(sourceSummary(id, null, false));
                return;
            }
            CollectOrchestrator.SourceOutcome row = outcome.sources().get(0);
            boolean previous = !row.ok()
                    && BuildJobs.hasNonEmptySnapshot(CollectOrchestrator.stagingDirectory(RepoPaths.root(), id));
            remember(sourceSummary(id, row, previous));
        } catch (IOException ex) {
            remember(messageOf(ex));
        }
    }

    static String sourceSummary(String id, CollectOrchestrator.SourceOutcome row, boolean previousSnapshot) {
        if (row == null || row.kind() == null) {
            return previousSnapshot
                    ? "Fetch failed for " + id + ". The previous staging snapshot was left in place."
                    : "Fetch failed for " + id + ". Nothing was staged.";
        }
        String kept = previousSnapshot
                ? "The previous staging snapshot was left in place."
                : "Nothing was staged.";
        return switch (row.kind()) {
            case COMPLETE ->
                    "Staging updated for " + id + ". Rebuild the catalog when you want students to see it.";
            case PARTIAL ->
                    "Partial staging saved for " + id + " (" + row.records()
                            + " notices). The previous snapshot is still on disk. Rebuild the catalog when you want students to see it.";
            case EMPTY -> "No notices for " + id + ". " + kept;
            case FAILED -> "Fetch failed for " + id + ". " + kept;
            case NOT_FINISHED -> "Fetch did not finish for " + id + ". " + kept;
        };
    }

    private void runProcess() {
        try {
            BuildJobs.RunSummary summary = commands.process();
            refreshCatalog.run();
            remember("Rebuilt the student catalog. " + summary.published() + " jobs, " + summary.examSeries()
                    + " calendars.");
        } catch (IOException ex) {
            remember(messageOf(ex));
        }
    }

    private CollectOrchestrator.Progress progress() {
        return new CollectOrchestrator.Progress() {
            @Override
            public void sourceStarted(String id) {
                synchronized (gate) {
                    currentSource = id;
                }
            }

            @Override
            public void sourceFinished(CollectOrchestrator.SourceOutcome outcome) {
                synchronized (gate) {
                    completed++;
                    currentSource = "";
                }
            }
        };
    }

    private void remember(String summary) {
        synchronized (gate) {
            lastSummary = summary;
            message = summary;
        }
    }

    private SourceSpec find(String id) throws IOException {
        for (SourceSpec spec : commands.registry()) {
            if (id.equals(spec.sourceId())) {
                return spec;
            }
        }
        return null;
    }

    private Map<String, Object> readReport() {
        Path file = RepoPaths.data().resolve("processed").resolve("collect-report.json");
        if (!Files.isRegularFile(file)) {
            return Map.of("sources", List.of());
        }
        try {
            Map<String, Object> report = mapper.readValue(file.toFile(), MAP);
            if (report == null) {
                return Map.of("sources", List.of());
            }
            return normalizeReport(report);
        } catch (IOException ex) {
            return Map.of("sources", List.of(), "message", "Could not read the last collect report.");
        }
    }

    /** Attach {@code outcome} to each source row. A Node {@code results} report is accepted as-is on disk. */
    static Map<String, Object> normalizeReport(Map<String, Object> report) {
        if (report == null) {
            return Map.of("sources", List.of());
        }
        Map<String, Object> copy = new LinkedHashMap<>(report);
        if (copy.get("sources") instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    rows.add(labelRow(stringKeyMap(raw)));
                }
            }
            copy.put("sources", rows);
        } else {
            copy.put("sources", rowsFrom(copy.get("results")));
        }
        return copy;
    }

    private static List<Map<String, Object>> rowsFrom(Object results) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(results instanceof List<?> list)) {
            return rows;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceId", raw.get("sourceId"));
            row.put("ok", raw.get("ok"));
            Object records = raw.get("records");
            row.put("records", records != null ? records : raw.get("written"));
            row.put("message", note(raw.get("message"), raw.get("errors")));
            if (raw.get("errors") instanceof List<?>) {
                row.put("errors", raw.get("errors"));
            }
            rows.add(labelRow(row));
        }
        return rows;
    }

    private static Map<String, Object> labelRow(Map<String, Object> row) {
        Object existing = row.get("outcome");
        if (existing != null && OUTCOMES.contains(String.valueOf(existing))) {
            row.put("outcome", String.valueOf(existing));
            return row;
        }
        String message = row.get("message") == null ? "" : String.valueOf(row.get("message")).trim();
        if ("not finished".equals(message)) {
            row.put("outcome", "not_finished");
            return row;
        }
        int records = countOf(row.get("records"));
        if (records == 0 && row.get("records") == null) {
            records = countOf(row.get("written"));
        }
        if (records > 0 && hasErrors(row.get("errors"))) {
            row.put("outcome", "partial");
        } else if (records > 0) {
            row.put("outcome", "complete");
        } else if (truthyOk(row.get("ok"))) {
            row.put("outcome", "empty");
        } else {
            row.put("outcome", "failed");
        }
        return row;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                row.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return row;
    }

    private static int countOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean hasErrors(Object errors) {
        return errors instanceof Collection<?> collection && !collection.isEmpty();
    }

    private static boolean truthyOk(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String note(Object message, Object errors) {
        String text = message == null ? "" : String.valueOf(message);
        if (text.isBlank() && errors instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map && map.get("message") != null) {
                text = String.valueOf(map.get("message"));
            } else if (first != null) {
                text = String.valueOf(first);
            }
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > 160) {
            return text.substring(0, 160) + "…";
        }
        return text;
    }

    private static String messageOf(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
