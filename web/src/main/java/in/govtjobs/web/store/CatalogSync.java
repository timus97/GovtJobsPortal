package in.govtjobs.web.store;

import in.govtjobs.web.config.GovtJobsProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

/**
 * Copies {@code data/processed} into Postgres after the sample seed.
 * Git JSON stays the source of truth. A JSON catalog does not sync.
 */
@Service
public class CatalogSync {

    private final CatalogStore catalog;
    private final GovtJobsProperties props;

    public CatalogSync(CatalogStore catalog, GovtJobsProperties props) {
        this.catalog = catalog;
        this.props = props;
    }

    @PostConstruct
    public void syncAfterSeed() {
        loadGitCatalog();
    }

    void loadGitCatalog() {
        if (!"postgres".equalsIgnoreCase(props.getCatalog().getSource())) {
            return;
        }
        catalog.syncFromGit();
    }
}
