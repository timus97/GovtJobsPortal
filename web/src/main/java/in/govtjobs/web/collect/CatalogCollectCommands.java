package in.govtjobs.web.collect;

import in.govtjobs.collect.BuildJobs;
import in.govtjobs.collect.CollectOrchestrator;
import in.govtjobs.collect.SourceSpec;
import java.io.IOException;
import java.util.List;

/** The Java collector commands the admin desk can start. Tests supply a fake. */
public interface CatalogCollectCommands {

    List<SourceSpec> registry() throws IOException;

    CollectOrchestrator.RunOutcome daily(CollectOrchestrator.Progress progress) throws IOException;

    CollectOrchestrator.RunOutcome source(String sourceId, CollectOrchestrator.Progress progress) throws IOException;

    BuildJobs.RunSummary process() throws IOException;
}
