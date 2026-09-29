package in.govtjobs.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.govtjobs.domain.labels.Labels;
import in.govtjobs.web.config.FeatureFlags;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.store.CatalogStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ViewAdviceTest {

    @Test
    void sampleCatalogRequiresPostgresSourceAndSampleRows() {
        FeatureFlags flags = new FeatureFlags();
        GovtJobsProperties props = new GovtJobsProperties();
        CatalogStore catalog = mock(CatalogStore.class);
        when(catalog.sampleMode()).thenReturn(true);
        ViewAdvice advice = new ViewAdvice(flags, props, catalog);

        props.getCatalog().setSource("json");
        assertThat(advice.sampleCatalog()).isFalse();
        props.getCatalog().setSource("postgres");
        assertThat(advice.sampleCatalog()).isTrue();
        props.getCatalog().setSource("POSTGRES");
        assertThat(advice.sampleCatalog()).isTrue();
        when(catalog.sampleMode()).thenReturn(false);
        assertThat(advice.sampleCatalog()).isFalse();
        assertThat(advice.flags()).isSameAs(flags);
    }

    @Test
    void labelMapsAreTheSharedCatalogLabels() {
        ViewAdvice advice = new ViewAdvice(new FeatureFlags(), new GovtJobsProperties(), mock(CatalogStore.class));
        assertThat(advice.orgTypeLabel()).isSameAs(Labels.ORG_TYPE_LABELS);
        assertThat(advice.selectionLabel()).isSameAs(Labels.SELECTION_LABELS);
        assertThat(advice.qualLabel()).isSameAs(Labels.QUAL_LABELS);
        assertThat(advice.statusLabel()).isSameAs(Labels.STATUS_LABELS);
        assertThat(advice.hasExamLabel()).isSameAs(Labels.HAS_EXAM_LABELS);
    }

    @Test
    void authFlagsFollowRolesAndIgnoreAnonymous() {
        ViewAdvice advice = new ViewAdvice(new FeatureFlags(), new GovtJobsProperties(), mock(CatalogStore.class));
        assertThat(advice.currentEmail(null)).isNull();
        assertThat(advice.isOps(null)).isFalse();
        assertThat(advice.isStudent(null)).isFalse();
        assertThat(advice.isAdmin(null)).isFalse();

        Authentication anon = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(advice.currentEmail(anon)).isEqualTo("anonymousUser");
        assertThat(advice.isOps(anon)).isFalse();
        assertThat(advice.isStudent(anon)).isFalse();
        assertThat(advice.isAdmin(anon)).isFalse();

        Authentication signedOut = mock(Authentication.class);
        when(signedOut.isAuthenticated()).thenReturn(false);
        when(signedOut.getName()).thenReturn("hidden@example.com");
        assertThat(advice.currentEmail(signedOut)).isNull();

        Authentication student = auth("student@example.com", "ROLE_STUDENT", "ROLE_OPS");
        assertThat(advice.currentEmail(student)).isEqualTo("student@example.com");
        assertThat(advice.isStudent(student)).isTrue();
        assertThat(advice.isOps(student)).isTrue();
        assertThat(advice.isAdmin(student)).isFalse();

        Authentication admin = auth("ops@example.com", "ROLE_ADMIN");
        assertThat(advice.isAdmin(admin)).isTrue();
        assertThat(advice.isStudent(admin)).isFalse();
    }

    private static Authentication auth(String name, String... roles) {
        return new UsernamePasswordAuthenticationToken(
                name,
                "n/a",
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
}
