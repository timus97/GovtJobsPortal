package in.govtjobs.web.collect;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin control for the Java registry collector. Separate from the priority-link crawl. */
@Controller
public class CatalogFetchController {

    private final CatalogCollectService collect;

    public CatalogFetchController(CatalogCollectService collect) {
        this.collect = collect;
    }

    @GetMapping("/ops/fetch")
    public String page(Model model) {
        model.addAttribute("nav", "fetch");
        model.addAttribute("status", collect.status());
        try {
            model.addAttribute("sources", collect.sources());
            model.addAttribute("enabledCount", collect.enabledCount());
        } catch (IOException ex) {
            model.addAttribute("sources", List.of());
            model.addAttribute("enabledCount", 0);
            model.addAttribute("notice", ex.getMessage());
        }
        return "ops/fetch";
    }

    @GetMapping(value = "/ops/fetch/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> status() {
        return collect.status();
    }

    @PostMapping("/ops/fetch/daily")
    public String daily(Principal principal, RedirectAttributes redirect) {
        return finish(collect.startDaily(name(principal)), redirect);
    }

    @PostMapping("/ops/fetch/source")
    public String source(Principal principal, @RequestParam(required = false) String sourceId, RedirectAttributes redirect) {
        return finish(collect.startSource(name(principal), sourceId), redirect);
    }

    @PostMapping("/ops/fetch/process")
    public String process(Principal principal, RedirectAttributes redirect) {
        return finish(collect.startProcess(name(principal)), redirect);
    }

    private static String finish(CatalogCollectService.Start start, RedirectAttributes redirect) {
        if (!start.started() && start.notice() != null && !start.notice().isBlank()) {
            redirect.addFlashAttribute("notice", start.notice());
        }
        return "redirect:/ops/fetch";
    }

    private static String name(Principal principal) {
        return principal == null ? "ops" : principal.getName();
    }
}
