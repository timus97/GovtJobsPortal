package in.govtjobs.web.catalog;

import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.JobStore.JobQuery;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class JobsController {

    private final JobStore jobs;
    private final GovtJobsProperties props;

    public JobsController(JobStore jobs, GovtJobsProperties props) {
        this.jobs = jobs;
        this.props = props;
    }

    @GetMapping("/jobs")
    public String list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String orgType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String qualification,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String selectionProcess,
            @RequestParam(defaultValue = "all") String hasExam,
            @RequestParam(required = false) String sourceId,
            @RequestParam(defaultValue = "lastDate") String sort,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        int pageSize = Math.max(1, props.getCatalog().getPageSize());
        JobQuery query = JobQuery.from(
                q, orgType, location, qualification, sector, status, selectionProcess, hasExam, sourceId, sort, page, pageSize);
        Map<String, Object> result = jobs.listJobs(query);
        model.addAttribute("nav", "jobs");
        model.addAttribute("title", "Search jobs");
        model.addAttribute("result", result);
        model.addAttribute("query", query);
        model.addAttribute("filters", jobs.filterOptions());
        return "catalog/jobs";
    }

    @GetMapping("/jobs/{id}")
    public String detail(@PathVariable String id, Model model) {
        Map<String, Object> job = jobs.getJobById(id);
        if (job == null) {
            model.addAttribute("nav", "jobs");
            model.addAttribute("missing", true);
            return "catalog/job-detail";
        }
        model.addAttribute("nav", "jobs");
        model.addAttribute("job", job);
        model.addAttribute("missing", false);
        return "catalog/job-detail";
    }
}
