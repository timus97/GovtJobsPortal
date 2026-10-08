package in.govtjobs.web.ops;

import in.govtjobs.web.config.FeatureFlags;
import in.govtjobs.web.security.SessionCookies;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.OperatorStore;
import in.govtjobs.web.store.StoreException;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OpsController {

    private static final Logger log = LoggerFactory.getLogger(OpsController.class);

    private final CollectQueue queue;
    private final JobStore jobs;
    private final OperatorStore operators;
    private final OpsLog logs;
    private final FeatureFlags flags;
    private final SessionCookies cookies;

    public OpsController(
            CollectQueue queue,
            JobStore jobs,
            OperatorStore operators,
            OpsLog logs,
            FeatureFlags flags,
            SessionCookies cookies) {
        this.queue = queue;
        this.jobs = jobs;
        this.operators = operators;
        this.logs = logs;
        this.flags = flags;
        this.cookies = cookies;
    }

    @PostMapping("/ops/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletResponse response,
            Model model) {
        if (!operators.verify(username, password)) {
            String reason = operators.findByUsername(username) == null ? "unknown_user" : "bad_password";
            log.warn("ops.login_failed username={} reason={}", safeUsername(username), reason);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            model.addAttribute("notice", "Username or password is not correct.");
            return "auth/ops-login";
        }
        Map<String, Object> row = operators.findByUsername(username);
        cookies.setOps(
                response,
                String.valueOf(row.get("id")),
                String.valueOf(row.get("username")),
                String.valueOf(row.getOrDefault("role", "operator")));
        logs.info("ops", "Operator signed in " + username);
        log.info("ops.login_ok username={}", safeUsername(username));
        return "redirect:/ops";
    }

    private static String safeUsername(String username) {
        if (username == null) {
            return "";
        }
        String cleaned = username.replaceAll("[\\r\\n]", "").trim();
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    @PostMapping("/ops/logout")
    public String logout(HttpServletResponse response) {
        cookies.clearOps(response);
        return "redirect:/ops/login";
    }

    @GetMapping("/ops")
    public String dashboard(Model model) {
        model.addAttribute("nav", "ops");
        model.addAttribute("jobs", queue.list(null));
        model.addAttribute("sources", jobs.getRegistry().get("sources"));
        model.addAttribute("progress", queue.progress());
        return "ops/dashboard";
    }

    @PostMapping("/ops/collect")
    public String paste(@RequestParam String url, @RequestParam(required = false) String sourceLabel, Model model) {
        try {
            queue.enqueue(url, sourceLabel);
            logs.info("collect", "Pasted " + url);
        } catch (Exception e) {
            model.addAttribute("notice", e.getMessage());
            model.addAttribute("jobs", queue.list(null));
            model.addAttribute("sources", jobs.getRegistry().get("sources"));
            model.addAttribute("progress", queue.progress());
            return "ops/dashboard";
        }
        return "redirect:/ops";
    }

    @PostMapping("/ops/operators")
    public String addOperator(@RequestParam String username, @RequestParam String password, Principal principal) {
        operators.create(username, password);
        logs.info("ops", principal.getName() + " created operator " + username);
        return "redirect:/ops";
    }

    @GetMapping("/ops/review")
    public String review(Model model) {
        model.addAttribute("nav", "review");
        model.addAttribute("items", queue.list("needs_review"));
        return "ops/review";
    }

    @GetMapping("/ops/runs/{id}")
    public String run(@PathVariable String id, Model model) {
        model.addAttribute("nav", "review");
        model.addAttribute("job", queue.get(id));
        return "ops/run";
    }

    @PostMapping("/ops/runs/{id}")
    public String runAction(
            @PathVariable String id,
            @RequestParam String action,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String officialUrl,
            @RequestParam(required = false) String lastDate,
            @RequestParam(required = false) String selectionProcess,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String hasExam,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirect) {
        try {
            if ("save".equals(action)) {
                queue.patchExtracted(id, submittedFacts(
                        title, organization, officialUrl, lastDate, selectionProcess, summary, hasExam));
            } else if ("publish".equals(action)) {
                queue.publishSubmitted(id, submittedFacts(
                        title, organization, officialUrl, lastDate, selectionProcess, summary, hasExam));
            } else if ("unpublish".equals(action)) {
                if (!flags.isUnpublish()) {
                    throw new StoreException("FEATURE", "Unpublish is disabled");
                }
                queue.setState(id, "unpublished", "Unpublished");
            } else if ("reject".equals(action)) {
                queue.setState(id, "rejected", reason == null ? "Rejected" : reason);
            }
        } catch (StoreException ex) {
            if (!"VALIDATION".equals(ex.code())) {
                throw ex;
            }
            redirect.addFlashAttribute("notice", ex.getMessage());
        }
        return "redirect:/ops/runs/" + id;
    }

    private static Map<String, Object> submittedFacts(
            String title,
            String organization,
            String officialUrl,
            String lastDate,
            String selectionProcess,
            String summary,
            String hasExam) {
        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("title", title);
        extracted.put("organization", organization);
        extracted.put("officialUrl", officialUrl);
        extracted.put("lastDate", lastDate);
        extracted.put("selectionProcess", selectionProcess);
        extracted.put("summary", summary);
        extracted.put("hasExam", "on".equals(hasExam) || "true".equals(hasExam));
        return extracted;
    }

    @GetMapping("/ops/sources")
    public String sources(Model model) {
        model.addAttribute("nav", "sources");
        model.addAttribute("registry", jobs.getRegistry());
        return "ops/sources";
    }

    @GetMapping("/ops/logs")
    public String logsPage(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("nav", "logs");
        model.addAttribute("items", logs.list(q, 200));
        model.addAttribute("q", q == null ? "" : q);
        return "ops/logs";
    }

    @GetMapping(value = "/ops/collect-progress", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> progress() {
        return queue.progress();
    }

    @GetMapping("/ops/jobs")
    public String catalogJobs() {
        return "redirect:/jobs";
    }

    @GetMapping("/ops/prepare")
    public String catalogPrepare() {
        return "redirect:/prepare";
    }
}
