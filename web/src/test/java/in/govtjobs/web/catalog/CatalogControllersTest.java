package in.govtjobs.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.JobStore.JobQuery;
import in.govtjobs.web.store.StudentStore;
import in.govtjobs.web.student.CurrentStudent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

class CatalogControllersTest {

    @Test
    void jobsListUsesPageSizeAndDetailCoversMissingAndFound() {
        JobStore jobs = mock(JobStore.class);
        GovtJobsProperties props = new GovtJobsProperties();
        props.getCatalog().setPageSize(0);
        when(jobs.listJobs(any())).thenReturn(Map.of("items", List.of(), "total", 0));
        when(jobs.filterOptions()).thenReturn(Map.of("boards", List.of()));
        when(jobs.getJobById("missing")).thenReturn(null);
        when(jobs.getJobById("job-1")).thenReturn(Map.of("id", "job-1", "title", "Clerk"));
        JobsController controller = new JobsController(jobs, props);
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.list(" q ", "central", "Delhi", "graduate", "Admin", "open", "cbt", "yes", "ssc", "lastDate", 2, model))
                .isEqualTo("catalog/jobs");
        ArgumentCaptor<JobQuery> query = ArgumentCaptor.forClass(JobQuery.class);
        verify(jobs).listJobs(query.capture());
        assertThat(query.getValue().limit()).isEqualTo(1);
        assertThat(query.getValue().page()).isEqualTo(2);
        assertThat(model.getAttribute("nav")).isEqualTo("jobs");
        assertThat(model.getAttribute("title")).isEqualTo("Search jobs");

        ExtendedModelMap missing = new ExtendedModelMap();
        assertThat(controller.detail("missing", missing)).isEqualTo("catalog/job-detail");
        assertThat(missing.getAttribute("missing")).isEqualTo(true);

        ExtendedModelMap found = new ExtendedModelMap();
        assertThat(controller.detail("job-1", found)).isEqualTo("catalog/job-detail");
        assertThat(found.getAttribute("missing")).isEqualTo(false);
        assertThat(found.getAttribute("job")).isNotNull();
    }

    @Test
    void preparePassesBoardThrough() {
        JobStore jobs = mock(JobStore.class);
        when(jobs.listExamSeries("SSC", null)).thenReturn(List.of(Map.of("id", "ssc-cgl")));
        when(jobs.filterOptions()).thenReturn(Map.of());
        PrepareController controller = new PrepareController(jobs);
        ExtendedModelMap blank = new ExtendedModelMap();
        assertThat(controller.prepare(null, blank)).isEqualTo("catalog/prepare");
        assertThat(blank.getAttribute("board")).isEqualTo("");

        ExtendedModelMap board = new ExtendedModelMap();
        assertThat(controller.prepare("SSC", board)).isEqualTo("catalog/prepare");
        assertThat(board.getAttribute("board")).isEqualTo("SSC");
        assertThat(board.getAttribute("series")).isNotNull();
    }

    @Test
    void matchSkipsIncompleteProfilesAndScoresCompleteOnes() {
        StudentStore students = mock(StudentStore.class);
        JobStore jobs = mock(JobStore.class);
        when(students.findInternalById("stu-1")).thenReturn(Map.of("id", "stu-1"));
        when(students.getProfile("stu-1")).thenReturn(null);
        MatchController controller = new MatchController(students, jobs, new CurrentStudent(students));
        ExtendedModelMap incomplete = new ExtendedModelMap();
        assertThat(controller.match(student(), incomplete)).isEqualTo("catalog/match");
        assertThat(incomplete.getAttribute("complete")).isEqualTo(false);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("reservationCategory", "UR");
        profile.put("dob", "2000-01-01");
        profile.put("highestEducation", "graduate");
        when(students.getProfile("stu-1")).thenReturn(profile);
        Map<String, Object> opportunity = new LinkedHashMap<>();
        opportunity.put("id", "job-1");
        opportunity.put("title", "Clerk");
        opportunity.put("officialUrl", "https://ssc.gov.in/");
        when(jobs.getOpportunities()).thenReturn(List.of(opportunity));
        ExtendedModelMap scored = new ExtendedModelMap();
        assertThat(controller.match(student(), scored)).isEqualTo("catalog/match");
        assertThat(scored.getAttribute("complete")).isEqualTo(true);
        assertThat(scored.getAttribute("matches")).isNotNull();
        assertThat(scored.getAttribute("excluded")).isNotNull();
        assertThat(scored.getAttribute("verifyBadge")).isNotNull();
    }

    private static UsernamePasswordAuthenticationToken student() {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "student@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        token.setDetails("stu-1");
        return token;
    }
}
