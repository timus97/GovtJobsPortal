package in.govtjobs.web.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.domain.job.JobSchema;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.support.JsonFiles;
import in.govtjobs.web.support.JsonMaps;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CollectQueue {

    private static final Logger log = LoggerFactory.getLogger(CollectQueue.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private final ObjectMapper mapper;
    private final OfficialUrlPolicy urls;
    private final GovtJobsProperties props;
    private final JobStore jobs;
    private final Object lock = new Object();
    private final HttpClient http;

    public CollectQueue(ObjectMapper mapper, OfficialUrlPolicy urls, GovtJobsProperties props, JobStore jobs) {
        this.mapper = mapper;
        this.urls = urls;
        this.props = props;
        this.jobs = jobs;
        int connect = Math.max(1, props.getOps().getConnectTimeoutSeconds());
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(connect))
                .build();
    }

    public Map<String, Object> enqueue(String url, String label) {
        URI uri = urls.requireAllowed(url);
        String trimmed = uri.toString();
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("id", UUID.randomUUID().toString());
        job.put("url", trimmed);
        job.put("host", host);
        job.put("sourceLabel", label);
        job.put("state", "needs_review");
        job.put("createdAt", Instant.now().toString());
        job.put("updatedAt", Instant.now().toString());
        job.put("extracted", extract(trimmed));
        job.put("timeline", List.of(Map.of("at", Instant.now().toString(), "state", "needs_review", "detail", "Pasted URL")));
        synchronized (lock) {
            Map<String, Object> data = load();
            List<Map<String, Object>> queued = jobsOf(data);
            queued.add(0, job);
            data.put("jobs", queued);
            data.put("updatedAt", Instant.now().toString());
            save(data);
        }
        log.info("ops.paste_queued id={} host={}", job.get("id"), host);
        return job;
    }

    public List<Map<String, Object>> list(String state) {
        return jobsOf(load()).stream()
                .filter(j -> state == null || state.isBlank() || state.equals(j.get("state")))
                .toList();
    }

    public Map<String, Object> get(String id) {
        return jobsOf(load()).stream().filter(j -> id.equals(j.get("id"))).findFirst().orElse(null);
    }

    public Map<String, Object> patchExtracted(String id, Map<String, Object> extracted) {
        synchronized (lock) {
            Map<String, Object> data = load();
            for (Map<String, Object> job : jobsOf(data)) {
                if (id.equals(job.get("id"))) {
                    Map<String, Object> cur = job.get("extracted") instanceof Map<?, ?> m
                            ? new LinkedHashMap<>((Map<String, Object>) m)
                            : new LinkedHashMap<>();
                    cur.putAll(extracted);
                    job.put("extracted", cur);
                    job.put("updatedAt", Instant.now().toString());
                    save(data);
                    return job;
                }
            }
            return null;
        }
    }

    public Map<String, Object> setState(String id, String state, String reason) {
        synchronized (lock) {
            Map<String, Object> data = load();
            for (Map<String, Object> job : jobsOf(data)) {
                if (id.equals(job.get("id"))) {
                    if ("published".equals(state) || "published_local".equals(state)) {
                        publishLocal(job);
                    } else if ("unpublished".equals(state)) {
                        unpublishLocal(job);
                    }
                    job.put("state", state);
                    if (reason != null) job.put("reason", reason);
                    job.put("updatedAt", Instant.now().toString());
                    List<Map<String, Object>> tl = job.get("timeline") instanceof List<?> l
                            ? new ArrayList<>((List<Map<String, Object>>) l)
                            : new ArrayList<>();
                    tl.add(Map.of("at", Instant.now().toString(), "state", state, "detail", reason == null ? state : reason));
                    job.put("timeline", tl);
                    save(data);
                    log.info("ops.job_state id={} state={}", id, state);
                    return job;
                }
            }
            return null;
        }
    }

    public Map<String, Object> progress() {
        Path p = RepoPaths.data().resolve("processed").resolve("collect-progress.json");
        if (!Files.isRegularFile(p)) {
            return Map.of("running", false, "phase", "idle");
        }
        try {
            return mapper.readValue(p.toFile(), MAP);
        } catch (Exception e) {
            return Map.of("running", false, "phase", "idle");
        }
    }

    @SuppressWarnings("unchecked")
    private void publishLocal(Map<String, Object> job) {
        Map<String, Object> extracted = job.get("extracted") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        String title = String.valueOf(extracted.getOrDefault("title", "Pasted opportunity"));
        String org = String.valueOf(extracted.getOrDefault("organization", job.get("host")));
        String url = String.valueOf(extracted.getOrDefault("officialUrl", job.get("url")));
        urls.requireAllowed(url);
        String lastDate = extracted.get("lastDate") == null ? null : String.valueOf(extracted.get("lastDate"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", JobSchema.stableJobId(Map.of(
                "organization", org, "title", title, "lastDate", lastDate == null ? "" : lastDate, "officialUrl", url)));
        row.put("title", title);
        row.put("organization", org);
        row.put("orgType", extracted.getOrDefault("orgType", "central"));
        row.put("selectionProcess", extracted.getOrDefault("selectionProcess", "direct_recruitment"));
        row.put("hasExam", Boolean.TRUE.equals(extracted.get("hasExam")));
        row.put("officialUrl", url);
        row.put("sourceId", "ops_paste");
        row.put("sourceName", job.getOrDefault("sourceLabel", "Ops paste"));
        row.put("sourceUrl", url);
        row.put("status", JobSchema.computeStatus(lastDate));
        row.put("lastDate", lastDate);
        row.put("summary", extracted.get("summary"));
        jobs.upsertPublishedJob(row);
        job.put("opportunityId", row.get("id"));
        log.info("ops.published_local id={} jobId={}", job.get("id"), row.get("id"));
    }

    private void unpublishLocal(Map<String, Object> job) {
        Object oppId = job.get("opportunityId");
        if (oppId == null) {
            return;
        }
        jobs.removePublishedJob(String.valueOf(oppId));
        log.info("ops.unpublished_local id={} jobId={}", job.get("id"), oppId);
    }

    private Map<String, Object> extract(String url) {
        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("officialUrl", url);
        extracted.put("title", url);
        int timeout = Math.max(1, props.getOps().getRequestTimeoutSeconds());
        int cap = Math.max(1024, props.getOps().getMaxBodyBytes());
        String ua = props.getOps().getUserAgent();
        if (ua == null || ua.isBlank()) {
            ua = "NoExamSarkariBot/1.0";
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", ua)
                    .GET()
                    .build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            extracted.put("httpStatus", res.statusCode());
            try (InputStream in = res.body()) {
                byte[] buf = in.readNBytes(cap + 1);
                if (buf.length > cap) {
                    buf = Arrays.copyOf(buf, cap);
                }
                Matcher m = TITLE.matcher(new String(buf, StandardCharsets.UTF_8));
                if (m.find()) {
                    extracted.put("title", m.group(1).replaceAll("\\s+", " ").trim());
                }
            }
            log.info("ops.extract url={} status={}", url, res.statusCode());
        } catch (Exception e) {
            log.warn("ops.extract_failed url={} err={}", url, e.toString());
            extracted.put("summary", "Could not fetch page — edit facts before publish.");
        }
        return extracted;
    }

    private Path path() {
        return RepoPaths.data().resolve("processed").resolve("collect-jobs.json");
    }

    private Map<String, Object> load() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("jobs", new ArrayList<>());
        Map<String, Object> raw = JsonFiles.read(path(), mapper, MAP, empty, log);
        if (raw == null) {
            return empty;
        }
        raw.putIfAbsent("jobs", new ArrayList<>());
        return raw;
    }

    private void save(Map<String, Object> data) {
        JsonFiles.writeAtomic(path(), data, mapper, log);
    }

    private List<Map<String, Object>> jobsOf(Map<String, Object> data) {
        return JsonMaps.listOf(data, "jobs");
    }
}
