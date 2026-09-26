package in.govtjobs.web.catalog;

import in.govtjobs.web.store.JobStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PrepareController {

    private final JobStore jobs;

    public PrepareController(JobStore jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/prepare")
    public String prepare(@RequestParam(required = false) String board, Model model) {
        model.addAttribute("nav", "prepare");
        model.addAttribute("board", board == null ? "" : board);
        model.addAttribute("series", jobs.listExamSeries(board, null));
        model.addAttribute("filters", jobs.filterOptions());
        return "catalog/prepare";
    }
}
