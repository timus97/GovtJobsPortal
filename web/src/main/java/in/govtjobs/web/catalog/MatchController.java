package in.govtjobs.web.catalog;

import in.govtjobs.domain.match.EligibilityFacts;
import in.govtjobs.domain.match.EligibilityMatch;
import in.govtjobs.domain.match.MatchException;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.StudentStore;
import in.govtjobs.web.student.CurrentStudent;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public String match(
            Principal principal,
            @RequestParam(name = "closed", required = false) String closed,
            Model model) {
        model.addAttribute("nav", "match");
        model.addAttribute("showClosed", "1".equals(closed));
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
            List<Map<String, Object>> opportunities = jobs.getOpportunities();
            Map<String, List<Map<String, Object>>> result =
                    EligibilityMatch.matchOpportunities(profile, opportunities);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            List<Map<String, Object>> matches = result.get("matches");
            List<Map<String, Object>> closedMatches = new ArrayList<>();
            List<Map<String, Object>> excluded = new ArrayList<>();
            // Closed is a status fail in the domain list. Keep it out of personal fails.
            List<Map<String, Object>> domainExcluded = result.get("excluded");
            if (domainExcluded != null) {
                for (Map<String, Object> row : domainExcluded) {
                    if (closedStatusFail(row)) {
                        closedMatches.add(row);
                    } else {
                        excluded.add(row);
                    }
                }
            }
            model.addAttribute("matches", matches == null ? List.of() : matches);
            model.addAttribute("excluded", excluded);
            model.addAttribute("closedMatches", closedMatches);
            model.addAttribute("verifyBadge", EligibilityMatch.VERIFY_BADGE);
            model.addAttribute("ageNotChecked", ageNotChecked(opportunities));
            log.info(
                    "match.scored candidates={} matches={} excluded={} closed={} ms={}",
                    opportunities == null ? 0 : opportunities.size(),
                    matches == null ? 0 : matches.size(),
                    excluded.size(),
                    closedMatches.size(),
                    ms);
        } catch (MatchException e) {
            log.warn("match.rejected {}", e.getMessage());
            model.addAttribute("complete", false);
            model.addAttribute("error", e.getMessage());
        }
        return "catalog/match";
    }

    private static boolean ageNotChecked(List<Map<String, Object>> opportunities) {
        if (opportunities == null || opportunities.isEmpty()) {
            return false;
        }
        for (Map<String, Object> opp : opportunities) {
            if (opp != null
                    && present(opp.get("ageMin"))
                    && present(opp.get("ageMax"))
                    && present(opp.get("ageAsOnDate"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean present(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private static boolean closedStatusFail(Map<String, Object> row) {
        Object reasons = row == null ? null : row.get("reasons");
        if (!(reasons instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> reason
                    && "status".equals(reason.get("rule"))
                    && "fail".equals(reason.get("outcome"))) {
                return true;
            }
        }
        return false;
    }
}
