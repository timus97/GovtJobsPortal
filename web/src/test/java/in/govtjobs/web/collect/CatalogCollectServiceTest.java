package in.govtjobs.web.collect;

import static org.assertj.core.api.Assertions.assertThat;

import in.govtjobs.collect.BuildJobs;
import in.govtjobs.collect.CollectOrchestrator;
import in.govtjobs.collect.SourceSpec;
import java.io.IOException;
import java.util.List;
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
                CollectOrchestrator.SourceOutcome outcome =
                        new CollectOrchestrator.SourceOutcome("demo", true, true, 1, "ok", List.of());
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
