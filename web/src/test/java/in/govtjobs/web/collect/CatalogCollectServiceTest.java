package in.govtjobs.web.collect;

import static org.assertj.core.api.Assertions.assertThat;

import in.govtjobs.collect.BuildJobs;
import in.govtjobs.collect.CollectOrchestrator;
import in.govtjobs.collect.SourceRunKind;
import in.govtjobs.collect.SourceSpec;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogCollectServiceTest {

    @Test
    void secondStartWaitsUntilTheFirstFinishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger refreshes = new AtomicInteger();
        CatalogCollectService service = new CatalogCollectService(commands(entered, release), refreshes::incrementAndGet);

        CatalogCollectService.Start first = service.startDaily("ada");
        assertThat(first.started()).isTrue();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.status().get("running")).isEqualTo(true);
        assertThat(service.startDaily("ada").notice()).isEqualTo("A fetch is already running.");

        release.countDown();
        waitUntilIdle(service);
        assertThat(refreshes.get()).isEqualTo(1);
        assertThat(String.valueOf(service.status().get("lastSummary"))).contains("rebuilt");
    }

    @Test
    void unknownSourceDoesNotStart() {
        CatalogCollectService service = new CatalogCollectService(commands(new CountDownLatch(1), new CountDownLatch(1)), () -> {});
        CatalogCollectService.Start start = service.startSource("ada", "missing");
        assertThat(start.started()).isFalse();
        assertThat(start.notice()).isEqualTo("Unknown source.");
        assertThat(service.status().get("running")).isEqualTo(false);
    }

    @Test
    void rebuildRefreshesTheCatalog() throws Exception {
        AtomicInteger refreshes = new AtomicInteger();
        CatalogCollectService service = new CatalogCollectService(commands(new CountDownLatch(1), new CountDownLatch(0)), refreshes::incrementAndGet);
        assertThat(service.startProcess("ada").started()).isTrue();
        waitUntilIdle(service);
        assertThat(refreshes.get()).isEqualTo(1);
        assertThat(String.valueOf(service.status().get("lastSummary"))).contains("2 jobs");
    }

    @Test
    void nodeReportRowsGainAnOutcomeWithoutRewritingTheFile() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("results", List.of(
                Map.of("sourceId", "zero", "ok", true, "written", 0),
                Map.of("sourceId", "full", "ok", true, "written", 2),
                Map.of("sourceId", "bad", "ok", false, "written", 0, "errors", List.of(Map.of("message", "HTTP 404"))),
                Map.of("sourceId", "part", "ok", true, "written", 1, "errors", List.of(Map.of("message", "pdf skipped"))),
                Map.of("sourceId", "stopped", "ok", false, "written", 0, "message", "not finished")));
        List<Map<String, Object>> rows = sources(CatalogCollectService.normalizeReport(report));
        assertThat(outcome(rows, "zero")).isEqualTo("empty");
        assertThat(outcome(rows, "full")).isEqualTo("complete");
        assertThat(outcome(rows, "bad")).isEqualTo("failed");
        assertThat(outcome(rows, "part")).isEqualTo("partial");
        assertThat(outcome(rows, "stopped")).isEqualTo("not_finished");

        Map<String, Object> kept = new LinkedHashMap<>();
        kept.put("sources", List.of(Map.of("sourceId", "demo", "outcome", "complete", "ok", true, "records", 1)));
        assertThat(outcome(sources(CatalogCollectService.normalizeReport(kept)), "demo")).isEqualTo("complete");
    }

    @Test
    void sourceSummaryNamesTheOutcome() {
        assertThat(CatalogCollectService.sourceSummary(
                        "demo",
                        new CollectOrchestrator.SourceOutcome("demo", SourceRunKind.COMPLETE, true, 2, null, List.of()),
                        false))
                .contains("Staging updated for demo");
        assertThat(CatalogCollectService.sourceSummary(
                        "demo",
                        new CollectOrchestrator.SourceOutcome(
                                "demo", SourceRunKind.PARTIAL, true, 1, "pdf skipped", List.of("pdf skipped")),
                        true))
                .contains("Partial staging saved for demo (1 notices)")
                .contains("previous snapshot");
        assertThat(CatalogCollectService.sourceSummary(
                        "demo",
                        new CollectOrchestrator.SourceOutcome(
                                "demo", SourceRunKind.EMPTY, true, 0, "no keepable notices", List.of()),
                        true))
                .isEqualTo("No notices for demo. The previous staging snapshot was left in place.");
        assertThat(CatalogCollectService.sourceSummary(
                        "demo",
                        new CollectOrchestrator.SourceOutcome(
                                "demo", SourceRunKind.FAILED, true, 0, "socket closed", List.of("socket closed")),
                        false))
                .isEqualTo("Fetch failed for demo. Nothing was staged.");
    }

    private static List<Map<String, Object>> sources(Map<String, Object> report) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        Object sources = report.get("sources");
        if (sources instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    raw.forEach((key, value) -> row.put(String.valueOf(key), value));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static String outcome(List<Map<String, Object>> rows, String sourceId) {
        for (Map<String, Object> row : rows) {
            if (sourceId.equals(row.get("sourceId"))) {
                return String.valueOf(row.get("outcome"));
            }
        }
        return "";
    }

    private static void waitUntilIdle(CatalogCollectService service) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (!Boolean.TRUE.equals(service.status().get("running"))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("collect did not finish");
    }

    private static CatalogCollectCommands commands(CountDownLatch entered, CountDownLatch release) {
        SourceSpec demo = new SourceSpec(
                "demo", "Demo board", "generic", "html_scrape", "", "central", List.of("https://example.gov.in/"), true);
        return new CatalogCollectCommands() {
            @Override
            public List<SourceSpec> registry() {
                return List.of(demo);
            }

            @Override
            public CollectOrchestrator.RunOutcome daily(CollectOrchestrator.Progress progress) {
                entered.countDown();
                await(release);
                progress.sourceStarted("demo");
                CollectOrchestrator.SourceOutcome outcome = new CollectOrchestrator.SourceOutcome(
                        "demo", SourceRunKind.COMPLETE, true, 1, "ok", List.of());
                progress.sourceFinished(outcome);
                return new CollectOrchestrator.RunOutcome(true, List.of(outcome));
            }

            @Override
            public CollectOrchestrator.RunOutcome source(String sourceId, CollectOrchestrator.Progress progress) {
                return new CollectOrchestrator.RunOutcome(false, List.of());
            }

            @Override
            public BuildJobs.RunSummary process() {
                return new BuildJobs.RunSummary(2, 1, "{}");
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
