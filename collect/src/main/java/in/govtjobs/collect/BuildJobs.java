package in.govtjobs.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import in.govtjobs.domain.job.ExamSeriesSchema;
import in.govtjobs.domain.job.JobSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Seed plus newest staging file per source, then enrich, quarantine, dedupe, and exam series.
 * Matches {@code scripts/process/buildJobs.js}, including atomic replacement of the processed JSON.
 */
public final class BuildJobs {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};
    private static final Pattern SCRAPE =
            Pattern.compile("scrape|playwright|pdf|highlights", Pattern.CASE_INSENSITIVE);
    private static final Pattern REVIEW_WORD = Pattern.compile("unknown|review", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOARD_UPSC = Pattern.compile("\\bupsc\\b");
    private static final Pattern BOARD_SSC = Pattern.compile("\\bssc\\b|staff selection");
    private static final Pattern BOARD_IBPS = Pattern.compile("\\bibps\\b");
    private static final Pattern BOARD_SBI = Pattern.compile("\\bsbi\\b|state bank");
    private static final Pattern BOARD_RRB = Pattern.compile("\\brrb\\b|railway recruitment");
    private static final Pattern BOARD_NTA = Pattern.compile("\\bnta\\b|ugc\\s*net");

    private BuildJobs() {}

    public record Options(boolean replacePublished) {
        public static Options from(String[] args) {
            boolean flag = false;
            if (args != null) {
                for (String arg : args) {
                    if ("--replace-published".equals(arg)) {
                        flag = true;
                    }
                }
            }
            return new Options(flag || "1".equals(System.getenv("REPLACE_PUBLISHED")));
        }
    }

    public record RunSummary(int published, int examSeries, String reportJson) {}

    public static RunSummary run(Path root) throws IOException {
        return run(root, Options.from(new String[0]));
    }

    public static RunSummary run(Path root, Options options) throws IOException {
        Options opts = options == null ? Options.from(new String[0]) : options;
        String startedAt = Instant.now().toString();
        long t0 = System.currentTimeMillis();
        Path data = root.resolve("data");
        Path processedDir = data.resolve("processed");
        Files.createDirectories(processedDir);

        List<Map<String, Object>> seed = readArray(data.resolve("seed").resolve("jobs.json"));
        Map<String, Object> overrides = readObject(data.resolve("seed").resolve("overrides.json"));
        Map<String, Object> aliasFile = readObject(data.resolve("sources").resolve("org_aliases.json"));
        Map<String, Object> aliases = asMap(aliasFile.get("aliases"));
        List<Map<String, Object>> staging = loadStagingRecords(data.resolve("staging"));
        List<String> forceExclude = stringList(overrides.get("forceExcludeIds"));
        List<String> forceInclude = stringList(overrides.get("forceIncludeIds"));
        Map<String, Map<String, Object>> fieldFixes = fieldFixes(overrides.get("fieldFixes"));
        Map<String, String> approvals = QuarantineApproval.load(data.resolve("seed").resolve("approvals.json"));

        List<Map<String, Object>> published = new ArrayList<>();
        List<Map<String, Object>> quarantine = new ArrayList<>();
        List<Map<String, Object>> calendarRaws = new ArrayList<>();
        int droppedExam = 0;
        int skippedCalendar = 0;
        int deduped = 0;
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();

        List<Map<String, Object>> incoming = new ArrayList<>(seed);
        incoming.addAll(staging);
        for (Map<String, Object> raw : incoming) {
            if (raw == null) {
                continue;
            }
            if (containsId(forceExclude, raw.get("id"))) {
                droppedExam++;
                continue;
            }
            Object cuetName = jsTruthy(raw.get("title")) ? raw.get("title") : raw.get("name");
            if (ExamSeriesSchema.isCuetName(cuetName)) {
                droppedExam++;
                continue;
            }
            if (ExamSeriesSchema.looksLikeCalendarRow(raw) && !jsTruthy(raw.get("lastDate"))) {
                calendarRaws.add(raw);
                skippedCalendar++;
                continue;
            }

            EnrichResult result = enrichRecord(raw, aliases, startedAt, approvals);
            if (result.quarantine()) {
                quarantine.add(quarantineRow(result.reason(), result.job()));
                continue;
            }

            Map<String, Object> job = result.job();
            Map<String, Object> fix = fieldFixes.get(text(job.get("id")));
            if (fix != null) {
                job = new LinkedHashMap<>(job);
                job.putAll(fix);
                job.put("updatedAt", startedAt);
                job.put("hasExam", Boolean.TRUE.equals(job.get("hasExam")));
            }
            if (containsId(forceInclude, job.get("id"))) {
                job.put("needsReview", false);
            }

            String key = dedupeKey(job);
            if (map.containsKey(key)) {
                deduped++;
                map.put(key, richer(map.get(key), job));
            } else {
                map.put(key, job);
            }
        }

        for (Map<String, Object> job : map.values()) {
            job.put("status", JobSchema.computeStatus(dateText(job.get("lastDate"))));
            job.put("needsReview", false);
            List<String> errors = JobSchema.isValidJob(job);
            if (!errors.isEmpty()) {
                quarantine.add(quarantineRow(String.join("; ", errors), job));
            } else {
                published.add(job);
            }
        }
        published.sort(BuildJobs::comparePublished);

        int keptPublished = 0;
        List<Map<String, Object>> previous = readArray(processedDir.resolve("jobs.json"));
        boolean replacePublished = opts.replacePublished();
        LinkedHashSet<String> wroteThisRun = new LinkedHashSet<>();
        for (Map<String, Object> row : staging) {
            if (row != null && jsTruthy(row.get("sourceId"))) {
                wroteThisRun.add(text(row.get("sourceId")));
            }
        }
        if (!replacePublished && previous.size() > published.size()) {
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (Map<String, Object> job : published) {
                seen.add(dedupeKey(job));
            }
            for (Map<String, Object> job : previous) {
                if (job == null) {
                    continue;
                }
                String key = dedupeKey(job);
                if (seen.contains(key) || !jsTruthy(job.get("lastDate")) || wroteThisRun.contains(text(job.get("sourceId")))) {
                    continue;
                }
                if (!JobSchema.isValidJob(job).isEmpty()
                        || JobLinkQuality.isGarbageJob(job).garbage()
                        || staleScrape(job)) {
                    continue;
                }
                published.add(job);
                seen.add(key);
                keptPublished++;
            }
            if (keptPublished > 0) {
                System.err.println(
                        "Kept "
                                + keptPublished
                                + " previously published jobs (incoming set was smaller). Set REPLACE_PUBLISHED=1 to replace instead.");
                published.sort(BuildJobs::comparePublished);
            }
        }

        String finishedAt = Instant.now().toString();
        Map<String, Object> registry = readObject(data.resolve("sources").resolve("registry.json"));
        int sourcesMonitored = 0;
        if (registry.get("sources") instanceof List<?> sources) {
            for (Object source : sources) {
                if (source instanceof Map<?, ?> row && jsTruthy(row.get("enabled"))) {
                    sourcesMonitored++;
                }
            }
        }

        List<Map<String, Object>> seedSeries = readArray(data.resolve("seed").resolve("exam_series.json"));
        List<Map<String, Object>> examSeries = buildExamSeries(seedSeries, calendarRaws, published, finishedAt);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startedAt", startedAt);
        report.put("finishedAt", finishedAt);
        report.put("durationMs", System.currentTimeMillis() - t0);
        report.put("inputSeed", seed.size());
        report.put("inputStaging", staging.size());
        report.put("inputTotal", incoming.size());
        report.put("published", published.size());
        report.put("quarantine", quarantine.size());
        report.put("droppedExam", droppedExam);
        report.put("skippedCalendar", skippedCalendar);
        report.put("keptPublished", keptPublished);
        report.put("examSeries", examSeries.size());
        report.put("deduped", deduped);
        report.put("sourcesMonitored", sourcesMonitored);

        Map<String, Object> stats = buildStats(published, report);
        List<Map<String, Object>> opportunities = new ArrayList<>();
        for (Map<String, Object> job : published) {
            Map<String, Object> copy = new LinkedHashMap<>(job);
            copy.put("kind", "opportunity");
            copy.put("examSeriesId", jsTruthy(job.get("examSeriesId")) ? job.get("examSeriesId") : null);
            opportunities.add(copy);
        }

        writeAtomic(processedDir.resolve("jobs.json"), published);
        writeAtomic(processedDir.resolve("opportunities.json"), opportunities);
        writeAtomic(processedDir.resolve("exam_series.json"), examSeries);
        writeAtomic(processedDir.resolve("stats.json"), stats);
        writeAtomic(processedDir.resolve("quarantine.json"), quarantine);
        writeAtomic(processedDir.resolve("run-report.json"), report);

        return new RunSummary(published.size(), examSeries.size(), MAPPER.writeValueAsString(report));
    }

    static void writeAtomic(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName().toString() + "." + ProcessHandle.current().pid() + ".tmp");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveFailed) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException("Failed to write " + file, e);
        }
    }

    private record EnrichResult(boolean quarantine, String reason, Map<String, Object> job) {}

    private static EnrichResult enrichRecord(
            Map<String, Object> raw, Map<String, Object> aliases, String collectedAt, Map<String, String> approvals) {
        String selectionText = selectionText(raw);
        String selectionProcess = jsTruthy(raw.get("selectionProcess")) ? text(raw.get("selectionProcess")) : null;
        Boolean hasExam = raw.get("hasExam") instanceof Boolean b ? b : null;
        boolean needsReview = jsTruthy(raw.get("needsReview"));
        boolean selectionOk = selectionProcess != null && JobSchema.SELECTION_PROCESSES.contains(selectionProcess);
        boolean curatedExam = Boolean.TRUE.equals(hasExam)
                && selectionOk
                && jsTruthy(raw.get("officialUrl"))
                && jsTruthy(raw.get("title"));
        boolean curatedNoExam = Boolean.FALSE.equals(hasExam)
                && selectionOk
                && jsTruthy(raw.get("officialUrl"))
                && jsTruthy(raw.get("title"));

        if (!curatedExam && !curatedNoExam) {
            JobSchema.ClassificationResult classified = JobSchema.classifySelectionText(selectionText);
            if (Boolean.TRUE.equals(classified.hasExam())) {
                hasExam = true;
                if (!selectionOk(selectionProcess) && classified.selectionProcess() != null) {
                    selectionProcess = classified.selectionProcess();
                }
                if (!selectionOk(selectionProcess)) {
                    needsReview = true;
                    selectionProcess = firstNonBlank(
                            classified.selectionProcess(), selectionProcess, "written_multi_stage");
                }
            } else if (!selectionOk(selectionProcess)) {
                if (classified.selectionProcess() != null) {
                    selectionProcess = classified.selectionProcess();
                    hasExam = false;
                } else {
                    needsReview = true;
                    selectionProcess = selectionProcess == null || selectionProcess.isBlank()
                            ? "interview_only"
                            : selectionProcess;
                    hasExam = false;
                }
            } else if (!Boolean.TRUE.equals(hasExam)) {
                hasExam = false;
            }
        }

        Map<String, Object> aliasHit = normalizeOrg(raw.get("organization"), aliases);
        String organization = aliasHit != null && jsTruthy(aliasHit.get("name"))
                ? text(aliasHit.get("name"))
                : text(raw.get("organization"));
        String orgType = firstNonBlank(
                jsTruthy(raw.get("orgType")) ? text(raw.get("orgType")) : null,
                aliasHit == null ? null : text(aliasHit.get("orgType")),
                "central");
        if (!JobSchema.ORG_TYPES.contains(orgType)) {
            orgType = "central";
        }
        String sector = firstNonBlank(
                jsTruthy(raw.get("sector")) ? text(raw.get("sector")) : null,
                aliasHit == null ? null : text(aliasHit.get("sector")),
                "Other");
        Object officialUrl = raw.get("officialUrl");
        Object lastDate = jsTruthy(raw.get("lastDate")) ? raw.get("lastDate") : null;
        String title = StagingRecords.cleanTitle(text(raw.get("title")));
        String id = jsTruthy(raw.get("id"))
                ? text(raw.get("id"))
                : JobSchema.stableJobId(
                        organization,
                        text(raw.get("title")),
                        lastDate == null ? "" : text(lastDate),
                        officialUrl == null ? "" : text(officialUrl));
        String now = collectedAt == null ? Instant.now().toString() : collectedAt;

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("id", id);
        job.put("title", title);
        job.put("organization", organization);
        job.put("orgType", orgType);
        job.put("sector", sector);
        job.put("location", jsTruthy(raw.get("location")) ? text(raw.get("location")) : "All India");
        job.put("vacancies", raw.containsKey("vacancies") ? raw.get("vacancies") : null);
        job.put("qualification", jsTruthy(raw.get("qualification")) ? raw.get("qualification") : null);
        job.put("experience", jsTruthy(raw.get("experience")) ? raw.get("experience") : null);
        job.put("salary", jsTruthy(raw.get("salary")) ? raw.get("salary") : null);
        job.put("selectionProcess", selectionProcess);
        job.put("hasExam", Boolean.TRUE.equals(hasExam));
        job.put("applicationMode", jsTruthy(raw.get("applicationMode")) ? raw.get("applicationMode") : null);
        job.put("notificationDate", jsTruthy(raw.get("notificationDate")) ? raw.get("notificationDate") : null);
        job.put("lastDate", lastDate);
        job.put("walkInDate", jsTruthy(raw.get("walkInDate")) ? raw.get("walkInDate") : null);
        job.put("officialUrl", officialUrl);
        job.put("sourceId", jsTruthy(raw.get("sourceId")) ? text(raw.get("sourceId")) : "seed_manual");
        job.put("sourceName", jsTruthy(raw.get("sourceName")) ? text(raw.get("sourceName")) : "Manual curator seed");
        job.put(
                "sourceUrl",
                jsTruthy(raw.get("sourceUrl"))
                        ? text(raw.get("sourceUrl"))
                        : (officialUrl == null ? "" : text(officialUrl)));
        job.put("summary", jsTruthy(raw.get("summary")) ? StagingRecords.cleanTitle(text(raw.get("summary"))) : "");
        job.put("eligibility", stringList(raw.get("eligibility")));
        job.put("processSteps", stringList(raw.get("processSteps")));
        job.put("documentsRequired", stringList(raw.get("documentsRequired")));
        job.put("status", JobSchema.computeStatus(dateText(lastDate)));
        job.put("needsReview", needsReview);
        job.put("notificationNo", jsTruthy(raw.get("notificationNo")) ? raw.get("notificationNo") : null);
        job.put("examSeriesId", jsTruthy(raw.get("examSeriesId")) ? raw.get("examSeriesId") : null);
        job.put("examDate", jsTruthy(raw.get("examDate")) ? raw.get("examDate") : null);
        job.put("collectedAt", jsTruthy(raw.get("collectedAt")) ? raw.get("collectedAt") : now);
        job.put(
                "collectorVersion",
                jsTruthy(raw.get("collectorVersion")) ? text(raw.get("collectorVersion")) : "process-v1");
        job.put("updatedAt", now);

        String approvedReason = approvals == null ? null : approvals.get(text(job.get("id")));
        if (approvedReason != null) {
            needsReview = false;
            job.put("needsReview", false);
            job.put("approvalReason", approvedReason);
        }

        boolean scrape = SCRAPE.matcher(text(raw.get("collectorVersion"))).find();
        if (approvedReason == null && scrape && !jsTruthy(lastDate) && !jsTruthy(raw.get("walkInDate"))) {
            job.put("needsReview", true);
            return new EnrichResult(true, "dateless_scrape", job);
        }
        if (approvedReason == null && staleScrape(job)) {
            return new EnrichResult(true, "stale_scrape", job);
        }
        if (needsReview
                && scrape
                && Boolean.TRUE.equals(raw.get("selectionInferred"))
                && jsTruthy(lastDate)
                && selectionOk(selectionProcess)
                && !REVIEW_WORD.matcher(text(raw.get("summary"))).find()) {
            needsReview = false;
            job.put("needsReview", false);
        }
        if (needsReview) {
            return new EnrichResult(true, "needs_review", job);
        }
        JobLinkQuality.Garbage junk = JobLinkQuality.isGarbageJob(job);
        if (junk.garbage()) {
            return new EnrichResult(true, "garbage_url:" + String.join(",", junk.reasons()), job);
        }
        List<String> errors = JobSchema.isValidJob(job);
        if (!errors.isEmpty()) {
            return new EnrichResult(true, String.join("; ", errors), job);
        }
        return new EnrichResult(false, null, job);
    }

    private static List<Map<String, Object>> buildExamSeries(
            List<Map<String, Object>> seedSeries,
            List<Map<String, Object>> calendarRaws,
            List<Map<String, Object>> jobs,
            String now) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> raw : seedSeries) {
            Map<String, Object> series = ExamSeriesSchema.normalizeExamSeries(raw, now);
            if (series == null || !ExamSeriesSchema.isValidExamSeries(series).isEmpty()) {
                continue;
            }
            byId.put(text(series.get("id")), series);
        }
        for (Map<String, Object> raw : calendarRaws) {
            Map<String, Object> series = seriesFromCalendarRaw(raw, now);
            if (series == null || !ExamSeriesSchema.isValidExamSeries(series).isEmpty()) {
                continue;
            }
            Map<String, Object> probe = new LinkedHashMap<>();
            probe.put("title", series.get("name"));
            probe.put("organization", series.get("board"));
            probe.put("sourceId", series.get("sourceId"));
            Map<String, Object> prev = null;
            for (Map<String, Object> candidate : byId.values()) {
                if (ExamSeriesSchema.seriesMatchesJob(candidate, probe)) {
                    prev = candidate;
                    break;
                }
            }
            if (prev == null) {
                prev = byId.get(text(series.get("id")));
            }
            if (prev != null) {
                Map<String, Object> incoming = new LinkedHashMap<>(series);
                incoming.put("id", prev.get("id"));
                byId.put(text(prev.get("id")), mergeSeries(prev, incoming));
            } else {
                byId.put(text(series.get("id")), series);
            }
        }
        for (Map<String, Object> job : jobs) {
            Map<String, Object> hit = null;
            for (Map<String, Object> candidate : byId.values()) {
                if (ExamSeriesSchema.seriesMatchesJob(candidate, job)) {
                    hit = candidate;
                    break;
                }
            }
            if (hit == null) {
                continue;
            }
            job.put("examSeriesId", hit.get("id"));
            boolean openApply = "open".equals(job.get("status")) || "closing_soon".equals(job.get("status"));
            if (openApply && jsTruthy(job.get("lastDate")) && !Boolean.TRUE.equals(hit.get("applyNever"))) {
                hit.put("linkedOpportunityIds", union(hit.get("linkedOpportunityIds"), List.of(text(job.get("id")))));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing((Map<String, Object> s) -> text(s.get("board")))
                .thenComparing(s -> text(s.get("name"))));
        return out;
    }

    private static Map<String, Object> seriesFromCalendarRaw(Map<String, Object> raw, String now) {
        String name = jsTruthy(raw.get("title")) ? text(raw.get("title")) : text(raw.get("name"));
        if (name.isBlank() || ExamSeriesSchema.isCuetName(name)) {
            return null;
        }
        Map<String, Object> titleProbe = new LinkedHashMap<>();
        titleProbe.put("title", name.trim());
        titleProbe.put("officialUrl", "https://example.gov.in/calendar/row");
        titleProbe.put("collectorVersion", "calendar-v1");
        if (JobLinkQuality.isGarbageJob(titleProbe).garbage()) {
            return null;
        }
        String board = jsTruthy(raw.get("board")) ? text(raw.get("board")).trim() : boardFromSource(raw);
        if (board == null || board.isBlank()) {
            return null;
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("board", board);
        input.put("name", name);
        input.put("cycle", raw.get("cycle"));
        input.put("sourceId", raw.get("sourceId"));
        input.put("officialUrl", raw.get("officialUrl"));
        input.put("notificationDate", raw.get("notificationDate"));
        input.put("lastDate", raw.get("lastDate"));
        input.put("examDate", raw.get("examDate"));
        input.put(
                "minEducation",
                jsTruthy(raw.get("qualification")) ? raw.get("qualification") : raw.get("minEducation"));
        return ExamSeriesSchema.normalizeExamSeries(input, now);
    }

    private static String boardFromSource(Map<String, Object> raw) {
        String blob = (text(raw.get("sourceId"))
                        + " "
                        + text(raw.get("organization"))
                        + " "
                        + text(raw.get("sourceName")))
                .toLowerCase(Locale.ROOT);
        if (BOARD_UPSC.matcher(blob).find()) {
            return "UPSC";
        }
        if (BOARD_SSC.matcher(blob).find()) {
            return "SSC";
        }
        if (BOARD_IBPS.matcher(blob).find()) {
            return "IBPS";
        }
        if (BOARD_SBI.matcher(blob).find()) {
            return "SBI";
        }
        if (BOARD_RRB.matcher(blob).find()) {
            return "RRB";
        }
        if (BOARD_NTA.matcher(blob).find()) {
            return "NTA";
        }
        return null;
    }

    private static Map<String, Object> mergeSeries(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            Object value = entry.getValue();
            if (value == null || (value instanceof String s && s.isEmpty()) || (value instanceof List<?> list && list.isEmpty())) {
                continue;
            }
            merged.put(entry.getKey(), value);
        }
        merged.put("aliases", union(existing.get("aliases"), incoming.get("aliases")));
        merged.put("linkedOpportunityIds", union(existing.get("linkedOpportunityIds"), incoming.get("linkedOpportunityIds")));
        return merged;
    }

    private static Map<String, Object> buildStats(List<Map<String, Object>> jobs, Map<String, Object> report) {
        Map<String, Integer> byOrgType = new LinkedHashMap<>();
        Map<String, Integer> bySector = new LinkedHashMap<>();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> bySource = new LinkedHashMap<>();
        Map<String, Integer> bySelection = new LinkedHashMap<>();
        for (Map<String, Object> job : jobs) {
            bump(byOrgType, text(job.get("orgType")));
            bump(bySector, text(job.get("sector")));
            bump(byStatus, text(job.get("status")));
            bump(bySource, text(job.get("sourceId")));
            bump(bySelection, text(job.get("selectionProcess")));
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", jobs.size());
        stats.put("open", byStatus.getOrDefault("open", 0));
        stats.put("closingSoon", byStatus.getOrDefault("closing_soon", 0));
        stats.put("closed", byStatus.getOrDefault("closed", 0));
        stats.put("byOrgType", byOrgType);
        stats.put("bySector", bySector);
        stats.put("byStatus", byStatus);
        stats.put("bySource", bySource);
        stats.put("bySelection", bySelection);
        stats.put("lastPipelineRunAt", report.get("finishedAt"));
        stats.put("sourcesMonitored", report.get("sourcesMonitored"));
        stats.put("examSeries", report.get("examSeries"));
        return stats;
    }

    private static void bump(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private static String dedupeKey(Map<String, Object> job) {
        if (jsTruthy(job.get("notificationNo"))) {
            return "n:" + text(job.get("notificationNo"));
        }
        if (jsTruthy(job.get("officialUrl"))) {
            String url = text(job.get("officialUrl")).toLowerCase(Locale.ROOT).split("\\?", 2)[0];
            return "u:" + url + "|" + text(job.get("title")).toLowerCase(Locale.ROOT);
        }
        return "t:"
                + text(job.get("organization")).toLowerCase(Locale.ROOT)
                + "|"
                + text(job.get("title")).toLowerCase(Locale.ROOT)
                + "|"
                + (jsTruthy(job.get("lastDate")) ? text(job.get("lastDate")) : "");
    }

    private static int comparePublished(Map<String, Object> a, Map<String, Object> b) {
        int rank = statusRank(text(a.get("status"))) - statusRank(text(b.get("status")));
        if (rank != 0) {
            return rank;
        }
        String ad = jsTruthy(a.get("lastDate")) ? text(a.get("lastDate")) : "9999";
        String bd = jsTruthy(b.get("lastDate")) ? text(b.get("lastDate")) : "9999";
        int dates = ad.compareTo(bd);
        if (dates != 0) {
            return dates;
        }
        String an = jsTruthy(a.get("notificationDate")) ? text(a.get("notificationDate")) : "";
        String bn = jsTruthy(b.get("notificationDate")) ? text(b.get("notificationDate")) : "";
        return bn.compareTo(an);
    }

    private static int statusRank(String status) {
        return switch (status) {
            case "closing_soon" -> 0;
            case "open" -> 1;
            case "closed" -> 2;
            default -> 9;
        };
    }

    private static Map<String, Object> richer(Map<String, Object> a, Map<String, Object> b) {
        return richness(a) >= richness(b) ? a : b;
    }

    private static int richness(Map<String, Object> job) {
        int score = jsTruthy(job.get("summary")) ? 2 : 0;
        score += stringList(job.get("eligibility")).size();
        score += stringList(job.get("processSteps")).size();
        if (jsTruthy(job.get("vacancies"))) {
            score++;
        }
        return score;
    }

    private static List<Map<String, Object>> loadStagingRecords(Path stagingDir) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        if (!Files.isDirectory(stagingDir)) {
            return records;
        }
        for (Path child : listChildren(stagingDir)) {
            String name = child.getFileName().toString();
            if (".gitkeep".equals(name)) {
                continue;
            }
            if (Files.isDirectory(child)) {
                List<Path> files = new ArrayList<>();
                for (Path candidate : listChildren(child)) {
                    String fileName = candidate.getFileName().toString();
                    if (".gitkeep".equals(fileName)) {
                        continue;
                    }
                    if (fileName.endsWith(".json") || fileName.endsWith(".jsonl")) {
                        files.add(candidate);
                    }
                }
                files.sort(Comparator.comparingLong(BuildJobs::mtimeMillis)
                        .reversed()
                        .thenComparing(path -> path.getFileName().toString()));
                if (files.isEmpty()) {
                    continue;
                }
                if ("ops_paste".equals(name)) {
                    for (Path file : files) {
                        records.addAll(parseStagingFile(file));
                    }
                } else {
                    records.addAll(parseStagingFile(files.get(0)));
                }
            } else if (name.endsWith(".json") || name.endsWith(".jsonl")) {
                records.addAll(parseStagingFile(child));
            }
        }
        return records;
    }

    private static List<Map<String, Object>> parseStagingFile(Path full) throws IOException {
        String name = full.getFileName().toString();
        String text = Files.readString(full);
        if (name.endsWith(".jsonl")) {
            List<Map<String, Object>> records = new ArrayList<>();
            for (String line : text.split("\n", -1)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode node = MAPPER.readTree(line);
                    if (node != null && node.isObject()) {
                        records.add(MAPPER.convertValue(node, MAP));
                    }
                } catch (IOException ignored) {
                    // A broken jsonl line is skipped. A broken json file still fails the run.
                }
            }
            return records;
        }
        JsonNode data = MAPPER.readTree(text);
        if (data == null || data.isNull()) {
            return List.of();
        }
        if (data.isArray()) {
            List<Map<String, Object>> rows = MAPPER.convertValue(data, LIST);
            return rows == null ? List.of() : rows;
        }
        if (data.isObject() && data.has("records") && data.get("records").isArray()) {
            List<Map<String, Object>> rows = MAPPER.convertValue(data.get("records"), LIST);
            return rows == null ? List.of() : rows;
        }
        if (data.isObject()) {
            return List.of(MAPPER.convertValue(data, MAP));
        }
        return List.of();
    }

    private static List<Map<String, Object>> readArray(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = MAPPER.readTree(file.toFile());
            if (node == null || node.isNull() || !node.isArray()) {
                throw new IllegalStateException("Expected a JSON array: " + file);
            }
            List<Map<String, Object>> rows = MAPPER.convertValue(node, LIST);
            return rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static Map<String, Object> readObject(Path file) {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = MAPPER.readTree(file.toFile());
            if (node == null || !node.isObject()) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> value = MAPPER.convertValue(node, MAP);
            return value == null ? new LinkedHashMap<>() : value;
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, Object> quarantineRow(String reason, Map<String, Object> job) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reason", reason);
        row.put("job", job);
        return row;
    }

    private static String selectionText(Map<String, Object> raw) {
        List<String> parts = new ArrayList<>();
        parts.add(text(raw.get("summary")));
        parts.add(text(raw.get("selectionProcess")));
        parts.addAll(stringList(raw.get("processSteps")));
        parts.addAll(stringList(raw.get("eligibility")));
        return String.join(" ", parts);
    }

    private static Map<String, Object> normalizeOrg(Object name, Map<String, Object> aliases) {
        String key = text(name).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        for (Map.Entry<String, Object> entry : aliases.entrySet()) {
            if (key.contains(entry.getKey()) && entry.getValue() instanceof Map<?, ?> meta) {
                return asMap(meta);
            }
        }
        return null;
    }

    private static Map<String, Map<String, Object>> fieldFixes(Object raw) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> fix) {
                out.put(String.valueOf(entry.getKey()), asMap(fix));
            }
        }
        return out;
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> out.put(String.valueOf(key), item));
        }
        return out;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static List<String> union(Object left, Object right) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.addAll(stringList(left));
        set.addAll(stringList(right));
        return new ArrayList<>(set);
    }

    private static boolean containsId(List<String> ids, Object id) {
        return id != null && ids.contains(String.valueOf(id));
    }

    /** Scraped rows whose application date is more than 18 months old stay out of the catalog. */
    private static boolean staleScrape(Map<String, Object> job) {
        if (job == null || !SCRAPE.matcher(text(job.get("collectorVersion"))).find() || !jsTruthy(job.get("lastDate"))) {
            return false;
        }
        String value = text(job.get("lastDate")).trim();
        if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
            value = value.substring(0, 10);
        }
        try {
            return LocalDate.parse(value).isBefore(LocalDate.now().minusMonths(18));
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static boolean selectionOk(String code) {
        return code != null && JobSchema.SELECTION_PROCESSES.contains(code);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String dateText(Object value) {
        if (!jsTruthy(value)) {
            return null;
        }
        return text(value);
    }

    private static boolean jsTruthy(Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof CharSequence s) {
            return !s.isEmpty();
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        return true;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<Path> listChildren(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static long mtimeMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
