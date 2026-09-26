package in.govtjobs.web.catalog;

import in.govtjobs.domain.match.EligibilityFacts;
import in.govtjobs.domain.match.EligibilityMatch;
import in.govtjobs.domain.match.MatchException;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.StudentStore;
import in.govtjobs.web.student.CurrentStudent;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);

    private final StudentStore students;
    private final JobStore jobs;
    private final CurrentStudent current;

    public MatchController(StudentStore students, JobStore jobs, CurrentStudent current) {
        this.students = students;
        this.jobs = jobs;
        this.current = current;
    }

    @GetMapping("/match")
    public String match(Principal principal, Model model) {
        model.addAttribute("nav", "match");
        String studentId = current.requireId(principal);
        Map<String, Object> profile = students.getProfile(studentId);
        boolean complete = EligibilityFacts.validateMatchProfile(profile).ok();
        model.addAttribute("complete", complete);
        model.addAttribute("profile", profile);
        if (!complete) {
            log.info("match.skipped reason=incomplete_profile");
            return "catalog/match";
        }
        try {
            long t0 = System.nanoTime();
            Map<String, List<Map<String, Object>>> result =
                    EligibilityMatch.matchOpportunities(profile, jobs.getOpportunities());
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            List<Map<String, Object>> matches = result.get("matches");
            List<Map<String, Object>> excluded = result.get("excluded");
            model.addAttribute("matches", matches);
            model.addAttribute("excluded", excluded);
            model.addAttribute("verifyBadge", EligibilityMatch.VERIFY_BADGE);
            log.info(
                    "match.scored candidates={} matches={} excluded={} ms={}",
                    jobs.getOpportunities().size(),
                    matches == null ? 0 : matches.size(),
                    excluded == null ? 0 : excluded.size(),
                    ms);
        } catch (MatchException e) {
            log.warn("match.rejected {}", e.getMessage());
            model.addAttribute("complete", false);
            model.addAttribute("error", e.getMessage());
        }
        return "catalog/match";
    }
}
