package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import in.govtjobs.domain.RepoPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildJobsTest {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ObjectMapper compact = new ObjectMapper();
    private final String closing = LocalDate.now().plusDays(3).toString();

    @Test
    void javaAndNodeAgreeOnAFrozenStagingSet(@TempDir Path root) throws Exception {
        Path javaRoot = root.resolve("java");
        Path nodeRoot = root.resolve("node");
        scaffold(javaRoot);
        scaffold(nodeRoot);
        Map<String, byte[]> live = snapshotLive();
        try {
            runNode(nodeRoot, false);
            BuildJobs.run(javaRoot, new BuildJobs.Options(false));
            assertSameCatalog(javaRoot, nodeRoot);
            assertFrozenRules(javaRoot);
        } finally {
            restoreIfChanged(live);
        }
    }

    @Test
    void replacePublishedDropsKeptRows(@TempDir Path root) throws Exception {
        Path javaRoot = root.resolve("java");
        Path nodeRoot = root.resolve("node");
        scaffoldReplace(javaRoot);
        scaffoldReplace(nodeRoot);
        Map<String, byte[]> live = snapshotLive();
        try {
            runNode(nodeRoot, true);
            BuildJobs.run(javaRoot, new BuildJobs.Options(true));
            assertSameCatalog(javaRoot, nodeRoot);
            assertThat(ids(read(javaRoot, "jobs.json"))).containsExactly("new-one");
        } finally {
            restoreIfChanged(live);
        }
    }

    @Test
    void pasteRowsAllSurviveAndASourceDirectoryKeepsOnlyItsNewestFile(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data/staging/ops_paste"));
        Files.createDirectories(root.resolve("data/staging/demo_board"));
        writeJson(root.resolve("data/staging/ops_paste/old.json"), List.of(
                curated("paste-old", "Old paste recruitment advt", "Paste Desk", "https://example.gov.in/paste/old", closing)));
        writeJson(root.resolve("data/staging/ops_paste/new.json"), List.of(
                curated("paste-new", "New paste recruitment advt", "Paste Desk", "https://example.gov.in/paste/new", closing)));
        Path older = root.resolve("data/staging/demo_board/older.json");
        Path newer = root.resolve("data/staging/demo_board/newer.json");
        Map<String, Object> oldRow = curated("board-old", "Old board recruitment advt", "Demo Board", "https://example.gov.in/board/old", closing);
        oldRow.put("sourceId", "demo_board");
        Map<String, Object> newRow = curated("board-new", "New board recruitment advt", "Demo Board", "https://example.gov.in/board/new", closing);
        newRow.put("sourceId", "demo_board");
        writeJson(older, List.of(oldRow));
        writeJson(newer, List.of(newRow));
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(5_000));
        BuildJobs.run(root, new BuildJobs.Options(false));
        assertThat(ids(read(root, "jobs.json"))).contains("paste-old", "paste-new", "board-new").doesNotContain("board-old");
    }

    @Test
    void corruptSeedIsIgnoredAndABrokenJsonFileFails(@TempDir Path root) throws Exception {
        Path javaRoot = root.resolve("java");
        Path nodeRoot = root.resolve("node");
        scaffoldCorruptSeed(javaRoot);
        scaffoldCorruptSeed(nodeRoot);
        Map<String, byte[]> live = snapshotLive();
        try {
            runNode(nodeRoot, false);
            BuildJobs.run(javaRoot, new BuildJobs.Options(false));
            assertSameCatalog(javaRoot, nodeRoot);
            assertThat(ids(read(javaRoot, "jobs.json"))).containsExactly("from-staging");
        } finally {
            restoreIfChanged(live);
        }

        Path broken = root.resolve("broken");
        Files.createDirectories(broken.resolve("data").resolve("staging").resolve("bad"));
        Files.createDirectories(broken.resolve("data").resolve("processed"));
        Files.writeString(broken.resolve("data").resolve("processed").resolve("jobs.json"), "STAY");
        Files.writeString(broken.resolve("data").resolve("staging").resolve("bad").resolve("broken.json"), "{");
        assertThatThrownBy(() -> BuildJobs.run(broken, new BuildJobs.Options(false)))
                .isInstanceOf(Exception.class);
        assertThat(Files.readString(broken.resolve("data").resolve("processed").resolve("jobs.json")))
                .isEqualTo("STAY");
    }

    @Test
    void failedAtomicWriteLeavesTheExistingFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("jobs.json");
        Files.writeString(file, "[1]");
        Map<String, Object> loop = new LinkedHashMap<>();
        loop.put("self", loop);
        assertThatThrownBy(() -> BuildJobs.writeAtomic(file, loop)).isInstanceOf(IOException.class);
        assertThat(Files.readString(file)).isEqualTo("[1]");
        try (var stream = Files.list(dir)) {
            assertThat(stream.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList())
                    .isEmpty();
        }
    }

    private void assertFrozenRules(Path root) throws IOException {
        JsonNode jobs = read(root, "jobs.json");
        JsonNode quarantine = read(root, "quarantine.json");
        JsonNode series = read(root, "exam_series.json");
        JsonNode report = read(root, "run-report.json");

        assertThat(field(job(jobs, "closing-soon"), "status")).isEqualTo("closing_soon");
        assertThat(field(job(jobs, "closed-post"), "status")).isEqualTo("closed");
        assertThat(field(job(jobs, "ssc-open"), "status")).isEqualTo("open");
        assertThat(field(job(jobs, "ssc-open"), "hasExam")).isEqualTo("true");

        JsonNode iocl = job(jobs, "iocl-walkin");
        assertThat(field(iocl, "organization")).isEqualTo("Indian Oil Corporation Limited");
        assertThat(field(iocl, "orgType")).isEqualTo("psu");
        assertThat(field(iocl, "sector")).isEqualTo("Energy");
        assertThat(field(iocl, "hasExam")).isEqualTo("false");
        assertThat(field(iocl, "selectionProcess")).isEqualTo("walk_in");

        assertThat(field(job(jobs, "bad-org-type"), "orgType")).isEqualTo("central");
        assertThat(field(job(jobs, "upsc-cse-2026"), "examSeriesId")).isEqualTo("upsc-cse");
        assertThat(field(job(jobs, "net-job"), "examSeriesId")).isEmpty();
        assertThat(field(job(jobs, "nurse-walkin"), "status")).isEqualTo("open");
        assertThat(field(job(jobs, "nurse-walkin"), "lastDate")).isEmpty();
        assertThat(field(jobByTitle(jobs, "Assistant grade advt no 18"), "hasExam")).isEqualTo("true");
        assertThat(field(jobByTitle(jobs, "Assistant grade advt no 18"), "selectionProcess")).isEqualTo("cbt");
        assertThat(field(job(jobs, "walk-classify"), "hasExam")).isEqualTo("false");
        assertThat(field(job(jobs, "walk-classify"), "selectionProcess")).isEqualTo("walk_in");
        assertThat(job(jobs, "project-associate")).isNotNull();

        assertThat(ids(jobs)).contains("kept-archive", "dedupe-rich", "url-rich", "jsonl-good", "loose-one", "wrapped-one");
        assertThat(ids(jobs)).doesNotContain(
                "cuet-ug",
                "drop-me",
                "stale-row",
                "dedupe-thin",
                "url-thin",
                "no-org",
                "bad-fix",
                "dateless-scrape",
                "ancient-drive",
                "old-dateless",
                "old-same-source",
                "old-invalid",
                "old-home");

        assertThat(reason(quarantine, "dateless-scrape")).isEqualTo("dateless_scrape");
        assertThat(reason(quarantine, "ancient-drive")).isEqualTo("stale_scrape");
        assertThat(field(quarantineJob(quarantine, "dateless-scrape"), "status")).isEqualTo("open");
        assertThat(field(quarantineJob(quarantine, "dateless-scrape"), "needsReview")).isEqualTo("true");
        assertThat(reason(quarantine, "field-assistant")).isEqualTo("needs_review");
        assertThat(reason(quarantine, "desk-helper")).isEqualTo("needs_review");
        assertThat(reason(quarantine, "no-org")).isEqualTo("organization required");
        assertThat(reason(quarantine, "bad-fix")).isEqualTo("invalid selectionProcess");
        assertThat(reason(quarantine, "careers-job")).isEqualTo("garbage_url:nav_title");
        assertThat(reason(quarantine, "homepage-job")).isEqualTo("garbage_url:homepage");
        assertThat(reason(quarantine, "index-job")).isEqualTo("garbage_url:index_only");
        assertThat(reason(quarantine, "low-score")).startsWith("garbage_url:");

        assertThat(ids(series)).containsExactly("ugc-net", "upsc-cse");
        JsonNode upsc = job(series, "upsc-cse");
        assertThat(field(upsc, "applyNever")).isEqualTo("false");
        assertThat(field(upsc, "expectedExam")).isEqualTo("2026-05-20");
        assertThat(field(upsc, "officialUrl")).isEqualTo("https://upsc.gov.in/exams/cse");
        assertThat(strings(upsc.get("linkedOpportunityIds"))).contains("upsc-cse-2026");
        assertThat(field(job(series, "ugc-net"), "applyNever")).isEqualTo("true");
        assertThat(strings(job(series, "ugc-net").get("linkedOpportunityIds"))).isEmpty();
        assertThat(report.get("keptPublished").asInt()).isPositive();
        assertThat(report.get("droppedExam").asInt()).isEqualTo(2);
        assertThat(report.get("skippedCalendar").asInt()).isEqualTo(2);
        assertThat(report.get("deduped").asInt()).isEqualTo(2);
    }

    private void assertSameCatalog(Path javaRoot, Path nodeRoot) throws IOException {
        JsonNode javaJobs = read(javaRoot, "jobs.json");
        JsonNode nodeJobs = read(nodeRoot, "jobs.json");
        assertThat(ids(javaJobs)).as("published ids").isEqualTo(ids(nodeJobs));
        for (JsonNode job : javaJobs) {
            JsonNode other = job(nodeJobs, job.get("id").asText());
            for (String key : List.of(
                    "status",
                    "title",
                    "organization",
                    "orgType",
                    "sector",
                    "hasExam",
                    "selectionProcess",
                    "examSeriesId",
                    "officialUrl",
                    "sourceId")) {
                assertThat(field(job, key)).as(job.get("id").asText() + " " + key).isEqualTo(field(other, key));
            }
        }

        assertThat(quarantineKeys(read(javaRoot, "quarantine.json")))
                .isEqualTo(quarantineKeys(read(nodeRoot, "quarantine.json")));
        assertThat(ids(read(javaRoot, "exam_series.json")))
                .isEqualTo(ids(read(nodeRoot, "exam_series.json")));
        for (JsonNode series : read(javaRoot, "exam_series.json")) {
            JsonNode other = job(read(nodeRoot, "exam_series.json"), series.get("id").asText());
            for (String key : List.of("name", "board", "applyNever", "expectedExam", "minEducation", "officialUrl")) {
                assertThat(field(series, key)).as(series.get("id").asText() + " " + key).isEqualTo(field(other, key));
            }
            assertThat(strings(series.get("linkedOpportunityIds")))
                    .isEqualTo(strings(other.get("linkedOpportunityIds")));
        }
        for (String key : List.of(
                "published",
                "quarantine",
                "droppedExam",
                "skippedCalendar",
                "keptPublished",
                "examSeries",
                "deduped",
                "inputSeed",
                "inputStaging",
                "inputTotal",
                "sourcesMonitored")) {
            assertThat(read(javaRoot, "run-report.json").get(key).asInt())
                    .as(key)
                    .isEqualTo(read(nodeRoot, "run-report.json").get(key).asInt());
        }
        JsonNode javaStats = read(javaRoot, "stats.json");
        JsonNode nodeStats = read(nodeRoot, "stats.json");
        for (String key : List.of("total", "open", "closingSoon", "closed")) {
            assertThat(javaStats.get(key).asInt()).as(key).isEqualTo(nodeStats.get(key).asInt());
        }
        for (String key : List.of("byOrgType", "bySector", "byStatus", "bySource", "bySelection")) {
            assertThat(intMap(javaStats.get(key))).as(key).isEqualTo(intMap(nodeStats.get(key)));
        }
        assertThat(ids(read(javaRoot, "opportunities.json"))).isEqualTo(ids(javaJobs));
        assertNoTemp(javaRoot);
        assertNoTemp(nodeRoot);
    }

    private void scaffold(Path root) throws IOException {
        List<Map<String, Object>> seed = new ArrayList<>();
        seed.add(exam(curated("ssc-open", "Combined Graduate Level advt no 1", "Staff Selection Commission", "https://ssc.gov.in/advt/cgl-1", "2099-06-01"), "written_multi_stage"));
        Map<String, Object> iocl = curated("iocl-walkin", "Refinery technician advt no 2", "IOCL Refinery", "https://iocl.gov.in/advt/refinery-2026", "2099-06-02");
        iocl.remove("orgType");
        iocl.remove("sector");
        iocl.put("selectionProcess", "walk_in");
        iocl.put("summary", "no GATE or CBT required");
        seed.add(iocl);
        seed.add(curated("closed-post", "Retired post advt no 3", "Staff Selection Commission", "https://ssc.gov.in/advt/retired-3", "2020-01-15"));
        seed.add(curated("closing-soon", "Closing window advt no 4", "Staff Selection Commission", "https://ssc.gov.in/advt/closing-4", closing));
        Map<String, Object> noOrg = curated("no-org", "Engagement of clerks advt no 12", "", "https://example.gov.in/advt/clerks-2026", "2099-04-01");
        noOrg.put("organization", "");
        seed.add(noOrg);
        Map<String, Object> badType = curated("bad-org-type", "Board messenger advt no 15", "Some Board", "https://example.gov.in/advt/messenger-15", "2099-06-12");
        badType.put("orgType", "municipality");
        seed.add(badType);
        seed.add(curated("drop-me", "Dropped advt no 16", "Some Board", "https://example.gov.in/advt/dropped-16", "2099-06-16"));
        seed.add(curated("cuet-ug", "CUET UG Admission", "National Testing Agency", "https://cuet.nta.nic.in/ug", "2099-06-17"));
        Map<String, Object> rich = curated("dedupe-rich", "Shared vacancy advt no 20", "Some Board", "https://example.gov.in/advt/shared-20", "2099-06-03");
        rich.put("notificationNo", "N-100");
        rich.put("summary", "Detailed notice");
        rich.put("eligibility", List.of("graduate"));
        rich.put("vacancies", 4);
        seed.add(rich);
        Map<String, Object> thin = curated("dedupe-thin", "Shared vacancy advt no 20 thin", "Some Board", "https://example.gov.in/advt/shared-20-thin", "2099-06-18");
        thin.put("notificationNo", "N-100");
        thin.put("summary", "");
        seed.add(thin);
        seed.add(exam(curated("net-job", "UGC NET fellowship advt no 8", "National Testing Agency", "https://ugcnet.nta.ac.in/advt/fellowship-8", "2099-06-08"), "written_multi_stage"));
        Map<String, Object> cbt = curated(null, "Assistant grade advt no 18", "Staff Selection Commission", "https://ssc.gov.in/advt/assistant-18", "2099-06-05");
        cbt.remove("hasExam");
        cbt.remove("selectionProcess");
        cbt.put("summary", "Computer based test for assistants");
        seed.add(cbt);
        Map<String, Object> walk = curated("walk-classify", "Depot associate advt no 19", "Food Corporation of India", "https://fci.gov.in/advt/depot-19", "2099-06-06");
        walk.remove("hasExam");
        walk.remove("selectionProcess");
        walk.put("summary", "walk-in interview at the depot");
        seed.add(walk);
        seed.add(curated("bad-fix", "Fixable clerk advt no 22", "Some Board", "https://example.gov.in/advt/fix-22", "2099-06-14"));

        List<Map<String, Object>> staging = new ArrayList<>();
        Map<String, Object> upsc = exam(curated("upsc-cse-2026", "Civil Services Examination 2026", "Union Public Service Commission", "https://upsc.gov.in/apply/cse-2026", "2099-06-04"), "written_multi_stage");
        upsc.put("sourceId", "upsc_advt");
        upsc.put("sourceName", "UPSC");
        upsc.put("sourceUrl", upsc.get("officialUrl"));
        staging.add(upsc);
        Map<String, Object> dateless = curated("dateless-scrape", "Field surveyor advt no 11", "ICAR", "https://icar.gov.in/advt/surveyor-11", null);
        dateless.put("collectorVersion", "html-scrape-v1");
        dateless.put("sourceId", "field_scrape");
        dateless.put("sourceUrl", dateless.get("officialUrl"));
        staging.add(dateless);
        Map<String, Object> ancient = curated(
                "ancient-drive",
                "Special recruitment drive advt no 4",
                "BEML",
                "https://bemlindia.in/advt/drive-2012.pdf",
                "2012-06-01");
        ancient.put("collectorVersion", "html-scrape-v1");
        ancient.put("sourceId", "beml_scrape");
        ancient.put("sourceUrl", ancient.get("officialUrl"));
        staging.add(ancient);
        Map<String, Object> nurse = curated("nurse-walkin", "Nurse engagement advt no 9", "AIIMS", "https://aiims.gov.in/advt/nurses-walkin", null);
        nurse.put("selectionProcess", "walk_in");
        nurse.put("collectorVersion", "html-scrape-v1");
        nurse.put("walkInDate", "2099-07-01");
        nurse.put("sourceId", "aiims_scrape");
        nurse.put("notificationDate", "2026-04-01");
        nurse.put("sourceUrl", nurse.get("officialUrl"));
        staging.add(nurse);
        Map<String, Object> cleared = curated("project-associate", "Project associate advt no 12", "ICAR", "https://icar.gov.in/advt/project-associate-2026", "2099-06-07");
        cleared.put("needsReview", true);
        cleared.put("selectionInferred", true);
        cleared.put("collectorVersion", "html-scrape-v1");
        cleared.put("summary", "Personal interview for project staff");
        cleared.put("sourceId", "icar_scrape");
        cleared.put("sourceUrl", cleared.get("officialUrl"));
        staging.add(cleared);
        Map<String, Object> reviewed = curated("field-assistant", "Field assistant advt no 13", "ICAR", "https://icar.gov.in/advt/field-assistant-13", "2099-06-19");
        reviewed.put("needsReview", true);
        reviewed.put("selectionInferred", true);
        reviewed.put("collectorVersion", "html-scrape-v1");
        reviewed.put("summary", "Selection under review");
        reviewed.put("sourceId", "icar_scrape");
        reviewed.put("sourceUrl", reviewed.get("officialUrl"));
        staging.add(reviewed);
        Map<String, Object> unknown = curated("desk-helper", "Desk helper advt no 14", "ICAR", "https://icar.gov.in/advt/desk-helper-14", "2099-06-20");
        unknown.remove("hasExam");
        unknown.remove("selectionProcess");
        unknown.put("summary", "See the attachment");
        unknown.put("sourceId", "icar_note");
        unknown.put("sourceUrl", unknown.get("officialUrl"));
        staging.add(unknown);
        staging.add(garbage("homepage-job", "Junior engineer advt no 7", "https://coalindia.in/"));
        staging.add(garbage("index-job", "Hello there", "https://org.gov.in/careers"));
        staging.add(garbage("careers-job", "Careers", "https://example.gov.in/advt/careers"));
        staging.add(garbage("whats-new", "What's New", "https://example.gov.in/advt/whats-new"));
        staging.add(garbage("skip-to", "Skip to main content", "https://example.gov.in/advt/skip"));
        staging.add(garbage("hindi-switch", "Switch हिंदी", "https://example.gov.in/advt/hindi"));
        staging.add(garbage("job-fair", "Annual job fair", "https://ncs.gov.in/advt/fair-2026"));
        Map<String, Object> low = garbage("low-score", "Weekly briefing note", "https://org.gov.in/files/briefing-note");
        low.put("collectorVersion", "pdf-scrape");
        staging.add(low);
        staging.add(garbage("tender-job", "Civil package advt no 30", "https://org.gov.in/tenders/civil-package"));
        Map<String, Object> urlRich = curated("url-rich", "Query twin advt no 21", "Some Board", "https://SSC.gov.in/advt/cgl?utm=1", "2099-06-13");
        urlRich.put("summary", "Detailed notice");
        urlRich.put("vacancies", 2);
        staging.add(urlRich);
        Map<String, Object> urlThin = curated("url-thin", "Query twin advt no 21", "Some Board", "https://ssc.gov.in/advt/cgl?utm=2", "2099-06-21");
        urlThin.put("summary", "");
        staging.add(urlThin);
        Map<String, Object> calendar = new LinkedHashMap<>();
        calendar.put("title", "Civil Services Examination");
        calendar.put("sourceId", "upsc_calendar");
        calendar.put("sourceName", "UPSC");
        calendar.put("organization", "UPSC");
        calendar.put("collectorVersion", "calendar-v1");
        calendar.put("officialUrl", "https://upsc.gov.in/exams/cse");
        calendar.put("notificationDate", "2026-02-01");
        calendar.put("examDate", "2026-05-20");
        calendar.put("qualification", "graduate");
        calendar.put("cycle", "2026");
        staging.add(calendar);
        Map<String, Object> misc = new LinkedHashMap<>();
        misc.put("title", "Town hall meeting");
        misc.put("sourceId", "misc_calendar");
        misc.put("organization", "Town Council");
        misc.put("collectorVersion", "calendar-v1");
        misc.put("officialUrl", "https://town.example.gov.in/calendar");
        staging.add(misc);

        Map<String, Object> stale = curated("stale-row", "Stale surveyor advt no 99", "ICAR", "https://icar.gov.in/advt/stale-99", "2099-06-22");
        Map<String, Object> jsonlJob = curated("jsonl-good", "Data entry operators advt no 4", "India Post", "https://indiapost.gov.in/advt/deo-2026", "2099-06-09");
        jsonlJob.put("sourceId", "india_post");
        jsonlJob.put("sourceName", "India Post");
        jsonlJob.put("sourceUrl", jsonlJob.get("officialUrl"));
        Map<String, Object> loose = curated("loose-one", "Garden staff advt no 5", "India Post", "https://indiapost.gov.in/advt/garden-5", "2099-06-10");
        loose.put("sourceId", "india_post_loose");
        loose.put("sourceUrl", loose.get("officialUrl"));
        Map<String, Object> wrapped = curated("wrapped-one", "Wrapper clerk advt no 6", "India Post", "https://indiapost.gov.in/advt/wrapper-6", "2099-06-11");
        wrapped.put("sourceId", "india_post_wrapped");
        wrapped.put("sourceUrl", wrapped.get("officialUrl"));

        writeJson(root.resolve("data/seed/jobs.json"), seed);
        writeJson(root.resolve("data/seed/exam_series.json"), List.of(upscSeries(), netSeries(), cuetSeries(), badSeries()));
        writeJson(root.resolve("data/seed/overrides.json"), overrides());
        writeJson(root.resolve("data/sources/org_aliases.json"), aliases());
        writeJson(root.resolve("data/sources/registry.json"), registry());
        writeJson(root.resolve("data/staging/board/old.json"), List.of(stale));
        writeJson(root.resolve("data/staging/board/new.json"), staging);
        writeJson(root.resolve("data/staging/wrapped/wrapped.json"), Map.of("records", List.of(wrapped)));
        writeJson(root.resolve("data/staging/loose.json"), loose);
        Files.createDirectories(root.resolve("data/staging/extra"));
        Files.writeString(root.resolve("data/staging/extra/one.jsonl"), compact.writeValueAsString(jsonlJob) + "\nthis is not json\n\n");
        Files.writeString(root.resolve("data/staging/null.json"), "null");
        Files.writeString(root.resolve("data/staging/.gitkeep"), "");
        Files.writeString(root.resolve("data/staging/readme.txt"), "ignore me");
        writeJson(root.resolve("data/processed/jobs.json"), previous());
        Files.setLastModifiedTime(root.resolve("data/staging/board/old.json"), FileTime.fromMillis(1_700_000_000_000L));
        Files.setLastModifiedTime(root.resolve("data/staging/board/new.json"), FileTime.fromMillis(1_800_000_000_000L));
    }

    private void scaffoldReplace(Path root) throws IOException {
        writeJson(root.resolve("data/seed/jobs.json"), List.of(curated("new-one", "New keeper advt no 1", "Some Board", "https://example.gov.in/advt/new-one", "2099-01-03")));
        writeJson(root.resolve("data/seed/exam_series.json"), List.of());
        writeJson(root.resolve("data/seed/overrides.json"), Map.of());
        writeJson(root.resolve("data/sources/org_aliases.json"), Map.of("aliases", Map.of()));
        writeJson(root.resolve("data/sources/registry.json"), Map.of("sources", List.of()));
        writeJson(root.resolve("data/processed/jobs.json"), List.of(
                curated("keep-me", "Old keeper advt no 2", "Some Board", "https://example.gov.in/advt/keep-me", "2099-01-01"),
                curated("also-keep", "Second keeper advt no 3", "Some Board", "https://example.gov.in/advt/also-keep", "2099-01-02")));
    }

    private void scaffoldCorruptSeed(Path root) throws IOException {
        Files.createDirectories(root.resolve("data/seed"));
        Files.writeString(root.resolve("data/seed/jobs.json"), "{");
        writeJson(root.resolve("data/seed/exam_series.json"), List.of());
        writeJson(root.resolve("data/seed/overrides.json"), Map.of());
        writeJson(root.resolve("data/sources/org_aliases.json"), Map.of("aliases", Map.of()));
        writeJson(root.resolve("data/sources/registry.json"), Map.of("sources", List.of()));
        Map<String, Object> job = curated("from-staging", "Staging survivor advt no 1", "Some Board", "https://example.gov.in/advt/survivor", "2099-03-03");
        job.put("sourceId", "staging_only");
        writeJson(root.resolve("data/staging/only/one.json"), List.of(job));
    }

    private List<Map<String, Object>> previous() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(archive("archive-" + i, "https://archive.example.gov.in/advt/post-" + i));
        }
        rows.add(archive("kept-archive", "https://archive.example.gov.in/advt/kept"));
        Map<String, Object> dup = archive("old-dup", "https://archive.example.gov.in/advt/old-dup");
        dup.put("notificationNo", "N-100");
        rows.add(dup);
        Map<String, Object> dateless = archive("old-dateless", "https://archive.example.gov.in/advt/old-dateless");
        dateless.remove("lastDate");
        rows.add(dateless);
        Map<String, Object> sameSource = archive("old-same-source", "https://archive.example.gov.in/advt/old-source");
        sameSource.put("sourceId", "upsc_advt");
        rows.add(sameSource);
        Map<String, Object> invalid = archive("old-invalid", "https://archive.example.gov.in/advt/old-invalid");
        invalid.put("title", "");
        rows.add(invalid);
        Map<String, Object> home = archive("old-home", "https://example.gov.in/");
        rows.add(home);
        return rows;
    }

    private Map<String, Object> archive(String id, String url) {
        Map<String, Object> job = curated(id, "Archive retention " + id + " advt", "Archive Desk", url, "2099-12-31");
        job.put("sourceId", "archive_manual");
        job.put("sourceName", "Archive");
        job.put("sourceUrl", url);
        job.put("status", "open");
        job.put("notificationDate", "2020-01-01");
        job.put("needsReview", false);
        return job;
    }

    private Map<String, Object> garbage(String id, String title, String url) {
        Map<String, Object> job = curated(id, title, "Some Board", url, "2099-05-01");
        job.put("sourceId", "garbage_scrape");
        job.put("sourceUrl", url);
        return job;
    }

    private Map<String, Object> curated(String id, String title, String org, String url, String lastDate) {
        Map<String, Object> job = new LinkedHashMap<>();
        if (id != null) {
            job.put("id", id);
        }
        job.put("title", title);
        job.put("organization", org);
        job.put("orgType", "central");
        job.put("sector", "Administration");
        job.put("selectionProcess", "interview_only");
        job.put("hasExam", false);
        job.put("officialUrl", url);
        job.put("sourceId", "seed_manual");
        job.put("sourceName", "Manual curator seed");
        job.put("sourceUrl", url);
        job.put("notificationDate", "2026-01-15");
        job.put("collectorVersion", "process-v1");
        job.put("collectedAt", "2026-01-16T00:00:00.000Z");
        job.put("location", "All India");
        job.put("summary", "");
        if (lastDate != null) {
            job.put("lastDate", lastDate);
        }
        return job;
    }

    private static Map<String, Object> exam(Map<String, Object> job, String selection) {
        job.put("hasExam", true);
        job.put("selectionProcess", selection);
        return job;
    }

    private static Map<String, Object> upscSeries() {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("id", "upsc-cse");
        series.put("board", "UPSC");
        series.put("name", "Civil Services");
        series.put("cycle", "2026");
        series.put("sourceId", "upsc_seed");
        series.put("officialUrl", "https://upsc.gov.in/");
        series.put("applyNever", false);
        series.put("minEducation", "graduate");
        series.put("aliases", List.of("CSE"));
        series.put("updatedAt", "2026-01-01T00:00:00.000Z");
        return series;
    }

    private static Map<String, Object> netSeries() {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("id", "ugc-net");
        series.put("board", "NTA");
        series.put("name", "UGC NET");
        series.put("sourceId", "nta_seed");
        series.put("officialUrl", "https://ugcnet.nta.ac.in/");
        series.put("applyNever", true);
        series.put("minEducation", "pg");
        series.put("updatedAt", "2026-01-01T00:00:00.000Z");
        return series;
    }

    private static Map<String, Object> cuetSeries() {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("id", "cuet-series");
        series.put("board", "NTA");
        series.put("name", "CUET UG");
        series.put("officialUrl", "https://cuet.nta.nic.in/");
        return series;
    }

    private static Map<String, Object> badSeries() {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("id", "bad-url-series");
        series.put("board", "SSC");
        series.put("name", "Bad Url");
        series.put("officialUrl", "http://ssc.gov.in/not-https");
        return series;
    }

    private static Map<String, Object> overrides() {
        Map<String, Object> fixes = new LinkedHashMap<>();
        fixes.put("iocl-walkin", Map.of("sector", "Energy"));
        fixes.put("bad-fix", Map.of("selectionProcess", "nope"));
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("forceExcludeIds", List.of("drop-me"));
        overrides.put("forceIncludeIds", List.of("iocl-walkin"));
        overrides.put("fieldFixes", fixes);
        return overrides;
    }

    private static Map<String, Object> aliases() {
        Map<String, Object> iocl = new LinkedHashMap<>();
        iocl.put("name", "Indian Oil Corporation Limited");
        iocl.put("orgType", "psu");
        iocl.put("sector", "Oil & Gas");
        Map<String, Object> aliases = new LinkedHashMap<>();
        aliases.put("iocl", iocl);
        aliases.put("hal", Map.of("name", "Hindustan Aeronautics Limited", "orgType", "psu", "sector", "Defence"));
        return Map.of("aliases", aliases);
    }

    private static Map<String, Object> registry() {
        return Map.of(
                "sources",
                List.of(
                        Map.of("id", "a", "enabled", true),
                        Map.of("id", "b", "enabled", false),
                        Map.of("id", "c", "enabled", true)));
    }

    private void writeJson(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writeValue(file.toFile(), value);
    }

    private void runNode(Path dataRoot, boolean replace) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("node", "scripts/process/buildJobs.js");
        builder.directory(RepoPaths.root().toFile());
        builder.environment().put("BUILD_JOBS_DATA_ROOT", dataRoot.toAbsolutePath().toString());
        if (replace) {
            builder.environment().put("REPLACE_PUBLISHED", "1");
        } else {
            builder.environment().remove("REPLACE_PUBLISHED");
        }
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new AssertionError("node buildJobs exited " + code + "\n" + output);
        }
    }

    private JsonNode read(Path root, String name) throws IOException {
        return mapper.readTree(root.resolve("data/processed").resolve(name).toFile());
    }

    private static List<String> ids(JsonNode rows) {
        List<String> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get("id").asText()));
        return ids;
    }

    private static JsonNode job(JsonNode rows, String id) {
        for (JsonNode row : rows) {
            if (id.equals(row.path("id").asText())) {
                return row;
            }
        }
        return null;
    }

    private static JsonNode jobByTitle(JsonNode rows, String title) {
        for (JsonNode row : rows) {
            if (title.equals(row.path("title").asText())) {
                return row;
            }
        }
        return null;
    }

    private static JsonNode quarantineJob(JsonNode rows, String id) {
        for (JsonNode row : rows) {
            if (id.equals(row.path("job").path("id").asText())) {
                return row.path("job");
            }
        }
        return null;
    }

    private static String reason(JsonNode rows, String id) {
        for (JsonNode row : rows) {
            if (id.equals(row.path("job").path("id").asText())) {
                return row.path("reason").asText();
            }
        }
        return "";
    }

    private static List<String> quarantineKeys(JsonNode rows) {
        List<String> keys = new ArrayList<>();
        rows.forEach(row -> keys.add(row.path("reason").asText()
                + "|"
                + row.path("job").path("id").asText()
                + "|"
                + row.path("job").path("status").asText()
                + "|"
                + row.path("job").path("needsReview").asText()));
        keys.sort(String::compareTo);
        return keys;
    }

    private static String field(JsonNode node, String name) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? "true" : "false";
        }
        return value.asText();
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> out.add(item.asText()));
        }
        out.sort(String::compareTo);
        return out;
    }

    private static Map<String, Integer> intMap(JsonNode node) {
        Map<String, Integer> out = new TreeMap<>();
        if (node != null) {
            node.fields().forEachRemaining(entry -> out.put(entry.getKey(), entry.getValue().asInt()));
        }
        return out;
    }

    private static void assertNoTemp(Path root) throws IOException {
        Path processed = root.resolve("data/processed");
        try (var stream = Files.list(processed)) {
            assertThat(stream.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList())
                    .isEmpty();
        }
    }

    private static Map<String, byte[]> snapshotLive() throws IOException {
        Path dir = RepoPaths.root().resolve("data").resolve("processed");
        Map<String, byte[]> snap = new LinkedHashMap<>();
        for (String name : List.of(
                "jobs.json",
                "opportunities.json",
                "exam_series.json",
                "stats.json",
                "quarantine.json",
                "run-report.json",
                "jobs.prev.json")) {
            Path file = dir.resolve(name);
            if (Files.isRegularFile(file)) {
                snap.put(name, Files.readAllBytes(file));
            }
        }
        return snap;
    }

    private static void restoreIfChanged(Map<String, byte[]> snap) throws IOException {
        Path dir = RepoPaths.root().resolve("data").resolve("processed");
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : snap.entrySet()) {
            Path file = dir.resolve(entry.getKey());
            byte[] now = Files.isRegularFile(file) ? Files.readAllBytes(file) : new byte[0];
            if (!Arrays.equals(entry.getValue(), now)) {
                Files.write(file, entry.getValue());
                changed.add(entry.getKey());
            }
        }
        if (!changed.isEmpty()) {
            throw new AssertionError("Live catalog was modified and has been restored: " + changed);
        }
    }
}
