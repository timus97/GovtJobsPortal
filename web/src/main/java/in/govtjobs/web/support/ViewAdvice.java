package in.govtjobs.web.support;

import in.govtjobs.domain.labels.Labels;
import in.govtjobs.web.config.FeatureFlags;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.store.CatalogStore;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ViewAdvice {

    private final FeatureFlags flags;
    private final GovtJobsProperties props;
    private final CatalogStore catalog;

    public ViewAdvice(FeatureFlags flags, GovtJobsProperties props, CatalogStore catalog) {
        this.flags = flags;
        this.props = props;
        this.catalog = catalog;
    }

    @ModelAttribute("sampleCatalog")
    public boolean sampleCatalog() {
        return "postgres".equalsIgnoreCase(props.getCatalog().getSource()) && catalog.sampleMode();
    }

    @ModelAttribute("flags")
    public FeatureFlags flags() {
        return flags;
    }

    @ModelAttribute("orgTypeLabel")
    public java.util.Map<String, String> orgTypeLabel() {
        return Labels.ORG_TYPE_LABELS;
    }

    @ModelAttribute("selectionLabel")
    public java.util.Map<String, String> selectionLabel() {
        return Labels.SELECTION_LABELS;
    }

    @ModelAttribute("qualLabel")
    public java.util.Map<String, String> qualLabel() {
        return Labels.QUAL_LABELS;
    }

    @ModelAttribute("statusLabel")
    public java.util.Map<String, String> statusLabel() {
        return Labels.STATUS_LABELS;
    }

    @ModelAttribute("hasExamLabel")
    public java.util.Map<String, String> hasExamLabel() {
        return Labels.HAS_EXAM_LABELS;
    }

    @ModelAttribute("currentEmail")
    public String currentEmail(Authentication auth) {
        return auth == null || !auth.isAuthenticated() ? null : auth.getName();
    }

    @ModelAttribute("isOps")
    public boolean isOps(Authentication auth) {
        return auth != null
                && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_OPS"::equals);
    }

    @ModelAttribute("isStudent")
    public boolean isStudent(Authentication auth) {
        return auth != null
                && auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_STUDENT"::equals);
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication auth) {
        return auth != null
                && auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_ADMIN"::equals);
    }
}
