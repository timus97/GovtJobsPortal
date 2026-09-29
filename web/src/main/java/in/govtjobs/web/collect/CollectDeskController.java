package in.govtjobs.web.collect;

import in.govtjobs.web.store.StoreException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CollectDeskController {

    private final CrawlDeskStore store;
    private final CrawlRunner runner;
    private final CrawlJobLogger jobs;

    public CollectDeskController(CrawlDeskStore store, CrawlRunner runner, CrawlJobLogger jobs) {
        this.store = store;
        this.runner = runner;
        this.jobs = jobs;
    }

    @GetMapping("/ops/pipeline")
    public String pipeline(Model model) {
        model.addAttribute("nav", "pipeline");
        model.addAttribute("summary", store.summary());
        model.addAttribute("runs", store.runs());
        List<Map<String, Object>> runs = store.runs();
        if (!runs.isEmpty() && "running".equals(runs.get(0).get("status"))) {
            model.addAttribute("liveRun", runs.get(0));
            model.addAttribute("liveSources", store.sourceResults(String.valueOf(runs.get(0).get("id"))));
            model.addAttribute("liveLog", jobs.forRun(String.valueOf(runs.get(0).get("id"))));
        }
        return "ops/pipeline";
    }

    @PostMapping("/ops/pipeline/runs")
    public String start(Principal principal) {
        String who = principal == null ? "ops" : principal.getName();
        String runId = runner.start(who);
        return "redirect:/ops/pipeline/runs/" + runId;
    }

    @GetMapping("/ops/pipeline/runs/{id}")
    public String run(@PathVariable String id, @RequestParam(required = false) String source, Model model) {
        Map<String, Object> run = store.run(id);
        if (run == null) {
            return "redirect:/ops/pipeline";
        }
        List<Map<String, Object>> sources = store.sourceResults(id);
        model.addAttribute("nav", "pipeline");
        model.addAttribute("run", run);
        model.addAttribute("sources", sources);
        model.addAttribute("log", jobs.forRun(id));
        String chosen = source;
        if ((chosen == null || chosen.isBlank()) && !sources.isEmpty()) {
            chosen = String.valueOf(sources.get(0).get("id"));
        }
        final String selected = chosen;
        model.addAttribute("selected", selected);
        if (selected != null && !selected.isBlank()) {
            model.addAttribute("output", store.noticesForSource(selected));
            model.addAttribute(
                    "selectedSource",
                    sources.stream().filter(s -> selected.equals(s.get("id"))).findFirst().orElse(null));
        }
        return "ops/pipeline-run";
    }

    @GetMapping("/ops/pipeline/review")
    public String review(@RequestParam(defaultValue = "waiting") String status, Model model) {
        model.addAttribute("nav", "collect-review");
        model.addAttribute("status", status);
        model.addAttribute("notices", store.queue(status));
        return "ops/pipeline-review";
    }

    @GetMapping("/ops/pipeline/notices/{id}")
    public String notice(@PathVariable String id, Model model) {
        Map<String, Object> notice = store.notice(id);
        if (notice == null) {
            return "redirect:/ops/pipeline/review";
        }
        model.addAttribute("nav", "collect-review");
        model.addAttribute("notice", notice);
        return "ops/pipeline-notice";
    }

    @PostMapping("/ops/pipeline/notices/{id}")
    public String decide(
            @PathVariable String id,
            @RequestParam String decision,
            RedirectAttributes redirect) {
        try {
            store.decide(id, decision);
        } catch (StoreException ex) {
            redirect.addFlashAttribute("decisionError", ex.getMessage());
        }
        return "redirect:/ops/pipeline/notices/" + id;
    }

    @GetMapping("/ops/pipeline/priority")
    public String priority(Model model) {
        model.addAttribute("nav", "priority");
        model.addAttribute("keywords", store.keywords());
        model.addAttribute("links", store.priorityLinks());
        return "ops/pipeline-priority";
    }

    @PostMapping("/ops/pipeline/keywords")
    public String addKeyword(@RequestParam String phrase, RedirectAttributes redirect) {
        try {
            store.addKeyword(phrase);
        } catch (StoreException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("notice", ex.getMessage());
        }
        return "redirect:/ops/pipeline/priority";
    }

    @PostMapping("/ops/pipeline/links")
    public String addLink(@RequestParam String url, @RequestParam(required = false) String label, RedirectAttributes redirect) {
        try {
            store.addLink(url, label);
        } catch (StoreException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("notice", ex.getMessage());
        }
        return "redirect:/ops/pipeline/priority";
    }
}
