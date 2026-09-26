package in.govtjobs.web.student;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.domain.desk.MockScore;
import in.govtjobs.domain.desk.StudyPlan;
import in.govtjobs.web.store.StudentStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CoachingController {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private final StudentStore students;
    private final ObjectMapper mapper;
    private final CurrentStudent current;

    public CoachingController(StudentStore students, ObjectMapper mapper, CurrentStudent current) {
        this.students = students;
        this.mapper = mapper;
        this.current = current;
    }

    @GetMapping("/desk/{id}/plan")
    public String plan(Principal principal, @PathVariable String id, Model model) {
        String sid = studentId(principal);
        Map<String, Object> item = students.getItem(sid, id);
        if (item == null || !"series".equals(item.get("kind"))) {
            return item == null ? "redirect:/dashboard" : "redirect:/desk/" + id;
        }
        String seriesId = String.valueOf(item.get("refId"));
        Map<String, Object> pack = readJson(coachingFile(seriesId, "syllabus"));
        if (pack == null) {
            model.addAttribute("missing", true);
            model.addAttribute("item", item);
            return "desk/plan";
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) pack.get("topics");
        Map<String, Object> plan = StudyPlan.buildPlan(topics, item.get("examDate"), null);
        Map<String, String> progress = students.topicProgress(sid, seriesId);
        model.addAttribute("nav", "desk");
        model.addAttribute("item", item);
        model.addAttribute("plan", plan);
        model.addAttribute("progress", progress);
        model.addAttribute("seriesId", seriesId);
        return "desk/plan";
    }

    @PostMapping("/desk/{id}/plan")
    public String tick(
            Principal principal,
            @PathVariable String id,
            @RequestParam String topicId,
            @RequestParam(defaultValue = "false") boolean done) {
        String sid = studentId(principal);
        Map<String, Object> item = students.getItem(sid, id);
        if (item != null && item.get("refId") != null) {
            students.setTopicDone(sid, String.valueOf(item.get("refId")), topicId, done);
        }
        return "redirect:/desk/" + id + "/plan";
    }

    @GetMapping("/desk/{id}/mock")
    public String mock(Principal principal, @PathVariable String id, Model model) {
        String sid = studentId(principal);
        Map<String, Object> item = students.getItem(sid, id);
        if (item == null || item.get("refId") == null) {
            return item == null ? "redirect:/dashboard" : "redirect:/desk/" + id;
        }
        String seriesId = String.valueOf(item.get("refId"));
        Map<String, Object> bank = readJson(coachingFile(seriesId, "mocks"));
        if (bank == null) {
            model.addAttribute("missing", true);
            model.addAttribute("item", item);
            return "desk/mock";
        }
        Map<String, Object> attempt = students.openAttempt(sid, seriesId, id);
        model.addAttribute("nav", "desk");
        model.addAttribute("item", item);
        model.addAttribute("bank", MockScore.publicBank(bank));
        model.addAttribute("attempt", attempt);
        return "desk/mock";
    }

    @PostMapping("/desk/{id}/mock/{attemptId}")
    public String submit(
            Principal principal,
            @PathVariable String id,
            @PathVariable String attemptId,
            @RequestParam Map<String, String> form) {
        String sid = studentId(principal);
        Map<String, Object> item = students.getItem(sid, id);
        if (item == null || item.get("refId") == null) {
            return "redirect:/dashboard";
        }
        String seriesId = String.valueOf(item.get("refId"));
        Map<String, Object> bank = readJson(coachingFile(seriesId, "mocks"));
        if (bank == null) {
            return "redirect:/desk/" + id;
        }
        Map<String, Object> answers = new LinkedHashMap<>();
        form.forEach((k, v) -> {
            if (k.startsWith("q_")) answers.put(k.substring(2), v);
        });
        Map<String, Object> scored = MockScore.scoreAttempt(bank, answers);
        students.submitAttempt(
                sid,
                attemptId,
                answers,
                ((Number) scored.get("score")).intValue(),
                ((Number) scored.get("total")).intValue());
        return "redirect:/desk/" + id + "/mock/" + attemptId;
    }

    @GetMapping("/desk/{id}/mock/{attemptId}")
    public String review(Principal principal, @PathVariable String id, @PathVariable String attemptId, Model model) {
        String sid = studentId(principal);
        Map<String, Object> item = students.getItem(sid, id);
        Map<String, Object> attempt = students.getAttempt(sid, attemptId);
        if (item == null || attempt == null) return "redirect:/dashboard";
        String seriesId = String.valueOf(item.get("refId"));
        Map<String, Object> bank = readJson(coachingFile(seriesId, "mocks"));
        if (bank == null) return "redirect:/desk/" + id;
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = attempt.get("answers") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        model.addAttribute("nav", "desk");
        model.addAttribute("item", item);
        model.addAttribute("attempt", attempt);
        model.addAttribute("review", MockScore.scoreAttempt(bank, answers));
        return "desk/mock-review";
    }

    private Path coachingFile(String seriesId, String folder) {
        if (seriesId == null || !seriesId.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        if (!"syllabus".equals(folder) && !"mocks".equals(folder)) {
            return null;
        }
        Path base = RepoPaths.data().resolve("coaching").resolve(folder).toAbsolutePath().normalize();
        Path resolved = base.resolve(seriesId + ".json").normalize();
        if (!resolved.startsWith(base)) {
            return null;
        }
        return resolved;
    }

    private Map<String, Object> readJson(Path path) {
        if (path == null || !Files.isRegularFile(path)) return null;
        try {
            return mapper.readValue(path.toFile(), MAP);
        } catch (Exception e) {
            return null;
        }
    }

    private String studentId(Principal principal) {
        return current.requireId(principal);
    }
}
