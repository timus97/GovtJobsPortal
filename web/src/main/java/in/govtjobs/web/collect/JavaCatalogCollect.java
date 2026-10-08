package in.govtjobs.web.collect;

import in.govtjobs.collect.BuildJobs;
import in.govtjobs.collect.CollectOrchestrator;
import in.govtjobs.collect.LiveSiteClient;
import in.govtjobs.collect.SourceSpec;
import in.govtjobs.domain.RepoPaths;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** In-process Java collector. This is the same work as {@code collect daily}, {@code source}, and {@code process}. */
@Component
public class JavaCatalogCollect implements CatalogCollectCommands {

    @Override
    public List<SourceSpec> registry() throws IOException {
        return CollectOrchestrator.registry(root());
    }

    @Override
    public CollectOrchestrator.RunOutcome daily(CollectOrchestrator.Progress progress) throws IOException {
        return CollectOrchestrator.daily(root(), new LiveSiteClient(), progress);
    }

    @Override
    public CollectOrchestrator.RunOutcome source(String sourceId, CollectOrchestrator.Progress progress)
            throws IOException {
        return CollectOrchestrator.source(root(), sourceId, new LiveSiteClient(), progress);
    }

    @Override
    public BuildJobs.RunSummary process() throws IOException {
        return BuildJobs.run(root(), new BuildJobs.Options(false));
    }

    private static Path root() {
        return RepoPaths.root();
    }
}
