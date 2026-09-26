package in.govtjobs.web.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.support.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class JobStore {

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JobStore.class);

    private final ObjectMapper mapper;
    private final CatalogStore catalog;
    private final GovtJobsProperties props;
    private final Object lock = new Object();
    private volatile Snapshot snapshot;

    public JobStore(ObjectMapper mapper) {
        this(mapper, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JobStore(ObjectMapper mapper, CatalogStore catalog, GovtJobsProperties props) {
        this.mapper = mapper;
        this.catalog = catalog;
        this.props = props;
    }

    private boolean postgresCatalog() {
        return catalog != null && props != null && "postgres".equalsIgnoreCase(props.getCatalog().getSource());
    }

    public List<Map<String, Object>> getJobs() {
        if (postgresCatalog()) {
            return catalog.approvedJobs();
        }
        return snapshot().jobs;
    }

    public List<Map<String, Object>> getOpportunities() {
        if (postgresCatalog()) {
            return catalog.approvedJobs();
        }
        List<Map<String, Object>> opps = snapshot().opportunities;
        return opps.isEmpty() ? getJobs() : opps;
    }

    public Map<String, Object> getJobById(String id) {
        if (id == null) return null;
        return getJobs().stream().filter(j -> id.equals(String.valueOf(j.get("id")))).findFirst().orElse(null);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = snapshot().stats;
        return stats.isEmpty() ? Map.of("total", 0) : stats;
    }

    public Map<String, Object> getRegistry() {
        return snapshot().registry;
    }

    public List<Map<String, Object>> getExamSeriesRaw() {
        if (postgresCatalog()) {
            return catalog.approvedSeries();
        }
        return snapshot().series;
    }

    public Map<String, Object> catalogHealth() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (postgresCatalog()) {
            out.put("source", "postgres");
            out.put("jobs", catalog.approvedJobs().size());
            out.put("examSeries", catalog.approvedSeries().size());
            out.put("sample", catalog.sampleMode());
            return out;
        }
        Snapshot snap = snapshot();
        out.put("source", "json");
        out.put("jobs", snap.jobs.size());
        out.put("examSeries", snap.series.size());
        return out;
    }

    public Map<String, Object> upsertPublishedJob(Map<String, Object> row) {
        if (row == null || row.get("id") == null) {
            throw new StoreException("VALIDATION", "Catalog row needs an id");
        }
        String id = String.valueOf(row.get("id"));
        if (!StudentFilePaths.isSafeId(id)) {
            throw new StoreException("VALIDATION", "Catalog row id is not safe");
        }
        synchronized (lock) {
            Path jobsPath = processed("jobs.json");
            List<Map<String, Object>> jobs = new ArrayList<>(readList(jobsPath));
            jobs.removeIf(j -> id.equals(String.valueOf(j.get("id"))));
            jobs.add(0, row);
            JsonFiles.writeAtomic(jobsPath, jobs, mapper, log);
            writePasteStaging(id, row);
            snapshot = null;
            return row;
        }
    }

    public boolean removePublishedJob(String id) {
        if (id == null || !StudentFilePaths.isSafeId(id)) {
            return false;
        }
        synchronized (lock) {
            boolean removed = removeIdFromList(processed("jobs.json"), id);
            removeIdFromList(processed("opportunities.json"), id);
            deletePasteStaging(id);
            if (removed) {
                snapshot = null;
            }
            return removed;
        }
    }

    public List<Map<String, Object>> getExamSeries() {
        Map<String, Map<String, Object>> byId = jobsById();
        return getExamSeriesRaw().stream().map(s -> decorateSeries(s, byId)).toList();
    }

    public Map<String, Object> getExamSeriesById(String id) {
        if (id == null) return null;
        return getExamSeries().stream().filter(s -> id.equals(String.valueOf(s.get("id")))).findFirst().orElse(null);
    }

    public Map<String, Object> listJobs(JobQuery query) {
        List<Map<String, Object>> jobs = applyFilters(getJobs(), query);
        int page = Math.max(1, query.page());
        int limit = Math.min(100, Math.max(1, query.limit()));
        int total = jobs.size();
        int start = (page - 1) * limit;
        List<Map<String, Object>> items =
                start >= total ? List.of() : jobs.subList(start, Math.min(total, start + limit));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", total);
        out.put("page", page);
        out.put("limit", limit);
        out.put("totalPages", Math.max(1, (int) Math.ceil(total / (double) limit)));
        return out;
    }

    public List<Map<String, Object>> listExamSeries(String board, String q) {
        List<Map<String, Object>> items = new ArrayList<>(getExamSeries());
        if (board != null && !board.isBlank()) {
            String b = board.trim().toLowerCase(Locale.ROOT);
            items.removeIf(s -> !b.equals(String.valueOf(s.get("board")).toLowerCase(Locale.ROOT)));
        }
        if (q != null && !q.isBlank()) {
            String term = q.toLowerCase(Locale.ROOT);
            items.removeIf(s -> {
                String blob = (s.getOrDefault("name", "") + " " + s.getOrDefault("board", "") + " "
                                + s.getOrDefault("cycle", ""))
                        .toLowerCase(Locale.ROOT);
                return !blob.contains(term);
            });
        }
        return items;
    }

    public Map<String, Object> filterOptions() {
        List<Map<String, Object>> jobs = getJobs();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orgTypes", distinct(jobs, "orgType"));
        out.put("locations", distinct(jobs, "location"));
        out.put("qualifications", distinct(jobs, "qualification"));
        out.put("sectors", distinct(jobs, "sector"));
        out.put("selectionProcesses", distinct(jobs, "selectionProcess"));
        out.put("sourceIds", distinct(jobs, "sourceId"));
        out.put("statuses", List.of("open", "closing_soon", "closed"));
        out.put("hasExam", List.of("all", "yes", "no"));
        out.put("boards", getExamSeries().stream().map(s -> String.valueOf(s.get("board"))).distinct().sorted().toList());
        return out;
    }

    public record JobQuery(
            String q,
            String orgType,
            String location,
            String qualification,
            String sector,
            String status,
            String selectionProcess,
            String hasExam,
            String sourceId,
            String sort,
            int page,
            int limit) {
        public static JobQuery from(
                String q,
                String orgType,
                String location,
                String qualification,
                String sector,
                String status,
                String selectionProcess,
                String hasExam,
                String sourceId,
                String sort,
                Integer page,
                Integer limit) {
            return new JobQuery(
                    blankToNull(q),
                    blankToNull(orgType),
                    blankToNull(location),
                    blankToNull(qualification),
                    blankToNull(sector),
                    blankToNull(status),
                    blankToNull(selectionProcess),
                    hasExam == null || hasExam.isBlank() ? "all" : hasExam,
                    blankToNull(sourceId),
                    sort == null || sort.isBlank() ? "lastDate" : sort,
                    page == null ? 1 : page,
                    limit == null ? 12 : limit);
        }
    }

    private List<Map<String, Object>> applyFilters(List<Map<String, Object>> jobs, JobQuery query) {
        List<Map<String, Object>> next = new ArrayList<>(jobs);
        if (query.q() != null) {
            String term = query.q().toLowerCase(Locale.ROOT);
            next.removeIf(j -> {
                String blob = (val(j, "title") + " " + val(j, "organization") + " " + val(j, "location") + " "
                                + val(j, "sector") + " " + val(j, "summary"))
                        .toLowerCase(Locale.ROOT);
                return !blob.contains(term);
            });
        }
        if (query.orgType() != null) {
            Set<String> types = Set.of(query.orgType().split(","));
            next.removeIf(j -> !types.contains(val(j, "orgType")));
        }
        if (query.location() != null) {
            String loc = query.location().toLowerCase(Locale.ROOT);
            next.removeIf(j -> !val(j, "location").toLowerCase(Locale.ROOT).contains(loc));
        }
        if (query.qualification() != null) {
            next.removeIf(j -> !query.qualification().equals(val(j, "qualification")));
        }
        if (query.sector() != null) {
            next.removeIf(j -> !query.sector().equals(val(j, "sector")));
        }
        if (query.status() != null) {
            Set<String> statuses = Set.of(query.status().split(","));
            next.removeIf(j -> !statuses.contains(val(j, "status")));
        } else {
            next.removeIf(j -> "closed".equals(val(j, "status")));
        }
        if (query.selectionProcess() != null) {
            next.removeIf(j -> !query.selectionProcess().equals(val(j, "selectionProcess")));
        }
        String exam = query.hasExam() == null ? "all" : query.hasExam().toLowerCase(Locale.ROOT);
        if (!exam.isBlank() && !"all".equals(exam)) {
            if (Set.of("yes", "true", "1").contains(exam)) {
                next.removeIf(j -> !Boolean.TRUE.equals(j.get("hasExam")));
            } else if (Set.of("no", "false", "0").contains(exam)) {
                next.removeIf(j -> !Boolean.FALSE.equals(j.get("hasExam")));
            }
        }
        if (query.sourceId() != null) {
            next.removeIf(j -> !query.sourceId().equals(val(j, "sourceId")));
        }
        if ("newest".equals(query.sort())) {
            next.sort(Comparator.comparing((Map<String, Object> j) -> val(j, "notificationDate")).reversed());
        } else {
            next.sort(Comparator.comparing(j -> {
                String d = val(j, "lastDate");
                return d.isEmpty() ? "9999" : d;
            }));
        }
        return next;
    }

    private Map<String, Map<String, Object>> jobsById() {
        return getJobs().stream()
                .filter(j -> j.get("id") != null)
                .collect(Collectors.toMap(j -> String.valueOf(j.get("id")), j -> j, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Object> decorateSeries(Map<String, Object> series, Map<String, Map<String, Object>> byId) {
        Map<String, Object> out = new LinkedHashMap<>(series);
        out.put("kind", "series");
        List<Map<String, Object>> linked = new ArrayList<>();
        Object ids = series.get("linkedOpportunityIds");
        if (ids instanceof List<?> list) {
            for (Object id : list) {
                Map<String, Object> job = byId.get(String.valueOf(id));
                if (job == null) continue;
                String status = val(job, "status");
                if (!"open".equals(status) && !"closing_soon".equals(status)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", job.get("id"));
                row.put("title", job.get("title"));
                row.put("status", job.get("status"));
                row.put("lastDate", job.get("lastDate"));
                row.put("officialUrl", job.get("officialUrl"));
                linked.add(row);
            }
        }
        out.put("linkedOpportunities", linked);
        out.put("canApply", !Boolean.TRUE.equals(series.get("applyNever")) && !linked.isEmpty());
        return out;
    }

    private List<String> distinct(List<Map<String, Object>> jobs, String key) {
        Set<String> set = new LinkedHashSet<>();
        for (Map<String, Object> job : jobs) {
            String v = val(job, key);
            if (!v.isBlank()) set.add(v);
        }
        return set.stream().sorted().toList();
    }

    private static String val(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private Path processed(String name) {
        return RepoPaths.data().resolve("processed").resolve(name);
    }

    private boolean removeIdFromList(Path path, String id) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        List<Map<String, Object>> rows = new ArrayList<>(readList(path));
        boolean removed = rows.removeIf(j -> id.equals(String.valueOf(j.get("id"))));
        if (removed) {
            JsonFiles.writeAtomic(path, rows, mapper, log);
        }
        return removed;
    }

    private void writePasteStaging(String id, Map<String, Object> row) {
        Path paste = RepoPaths.data().resolve("staging").resolve("ops_paste").resolve(id + ".json");
        JsonFiles.writeAtomic(paste, row, mapper, log);
    }

    private void deletePasteStaging(String id) {
        Path paste = RepoPaths.data().resolve("staging").resolve("ops_paste").resolve(id + ".json");
        try {
            Files.deleteIfExists(paste);
        } catch (Exception e) {
            log.warn("staging.delete_failed id={} err={}", id, e.toString());
        }
    }

    private Snapshot snapshot() {
        Path jobs = processed("jobs.json");
        Path series = processed("exam_series.json");
        Path opps = processed("opportunities.json");
        Path stats = processed("stats.json");
        Path registry = RepoPaths.data().resolve("sources").resolve("registry.json");
        long stamp = JsonFiles.mtime(jobs)
                ^ JsonFiles.mtime(series)
                ^ JsonFiles.mtime(opps)
                ^ JsonFiles.mtime(stats)
                ^ JsonFiles.mtime(registry);
        Snapshot current = snapshot;
        if (current != null && current.stamp == stamp) {
            return current;
        }
        synchronized (lock) {
            current = snapshot;
            if (current != null && current.stamp == stamp) {
                return current;
            }
            List<Map<String, Object>> jobList = readList(jobs);
            Snapshot next = new Snapshot(
                    stamp,
                    java.time.Instant.now().toString(),
                    jobList,
                    readList(opps),
                    readList(series),
                    readMap(stats),
                    readMap(registry));
            snapshot = next;
            log.info(
                    "catalog.loaded jobs={} series={} opps={} path={}",
                    next.jobs.size(),
                    next.series.size(),
                    next.opportunities.size(),
                    jobs);
            return next;
        }
    }

    private List<Map<String, Object>> readList(Path path) {
        List<Map<String, Object>> list = JsonFiles.read(path, mapper, LIST_MAP, List.of(), log);
        return list == null ? List.of() : list;
    }

    private Map<String, Object> readMap(Path path) {
        Map<String, Object> map = JsonFiles.read(path, mapper, MAP, Map.of(), log);
        return map == null ? Map.of() : map;
    }

    private record Snapshot(
            long stamp,
            String loadedAt,
            List<Map<String, Object>> jobs,
            List<Map<String, Object>> opportunities,
            List<Map<String, Object>> series,
            Map<String, Object> stats,
            Map<String, Object> registry) {}
}
