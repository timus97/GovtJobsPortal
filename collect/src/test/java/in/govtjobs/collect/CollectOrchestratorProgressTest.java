package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectOrchestratorProgressTest {

    @Test
    void sourceTellsTheListenerWhenItStartsAndFinishes(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data").resolve("sources"));
        Files.writeString(
                root.resolve("data").resolve("sources").resolve("registry.json"),
                """
                {"sources":[{"sourceId":"demo_board","name":"Demo","enabled":true,"method":"html_scrape","listUrls":["https://example.gov.in/careers"]}]}
                """);
        List<String> seen = new ArrayList<>();
        SiteClient client = url -> new SiteClient.PageBody(url, "<html><title>Careers</title><body></body></html>");
        CollectOrchestrator.RunOutcome outcome = CollectOrchestrator.source(
                root,
                "demo_board",
                client,
                new CollectOrchestrator.Progress() {
                    @Override
                    public void sourceStarted(String sourceId) {
                        seen.add("start:" + sourceId);
                    }

                    @Override
                    public void sourceFinished(CollectOrchestrator.SourceOutcome finished) {
                        seen.add("done:" + finished.sourceId());
                    }
                });
        assertThat(seen).containsExactly("start:demo_board", "done:demo_board");
        assertThat(outcome.sources()).hasSize(1);
        assertThat(outcome.usable()).isFalse();
    }
}
