package in.govtjobs.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.store.JobStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

class SearchHonestyTest {

    @Test
    void listPutsOpenClosingSoonAndClosedCountsFromGetJobs() {
        JobStore jobs = mock(JobStore.class);
        Map<String, Object> open = new LinkedHashMap<>();
        open.put("id", "future-open");
        open.put("status", "open");
        open.put("lastDate", "2099-12-31");
        Map<String, Object> closingSoon = new LinkedHashMap<>();
        closingSoon.put("id", "closing-soon");
        closingSoon.put("status", "closing_soon");
        Map<String, Object> closed = new LinkedHashMap<>();
        closed.put("id", "closed-row");
        closed.put("status", "closed");
        closed.put("lastDate", "2020-01-01");
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("id", "needs-review");
        other.put("status", "needs_review");
        when(jobs.getJobs()).thenReturn(List.of(open, closingSoon, closed, other));
        when(jobs.listJobs(any())).thenReturn(Map.of("items", List.of(), "total", 0));
        when(jobs.filterOptions()).thenReturn(Map.of());
        JobsController controller = new JobsController(jobs, new GovtJobsProperties());
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.list(null, null, null, null, null, null, null, "all", null, "lastDate", 1, model))
                .isEqualTo("catalog/jobs");

        assertThat(statusCounts(model))
                .containsEntry("open", 1)
                .containsEntry("closing_soon", 1)
                .containsEntry("closed", 1);
        assertThat(model.getAttribute("result")).isEqualTo(Map.of("items", List.of(), "total", 0));
    }

    @Test
    void futureOpenRowAndClosedRowCountAsOneEach() {
        JobStore jobs = mock(JobStore.class);
        Map<String, Object> open = new LinkedHashMap<>();
        open.put("id", "future-open");
        open.put("status", "open");
        open.put("lastDate", "2099-06-01");
        Map<String, Object> closed = new LinkedHashMap<>();
        closed.put("id", "closed-row");
        closed.put("status", "closed");
        closed.put("lastDate", "2020-01-01");
        when(jobs.getJobs()).thenReturn(List.of(open, closed));
        when(jobs.listJobs(any())).thenReturn(Map.of("items", List.of(open), "total", 1));
        when(jobs.filterOptions()).thenReturn(Map.of());
        JobsController controller = new JobsController(jobs, new GovtJobsProperties());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.list(null, null, null, null, null, null, null, "all", null, "lastDate", 1, model);

        assertThat(statusCounts(model)).containsEntry("open", 1).containsEntry("closed", 1);
        assertThat(statusCounts(model)).containsEntry("closing_soon", 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> statusCounts(ExtendedModelMap model) {
        return (Map<String, Integer>) model.getAttribute("statusCounts");
    }
}
