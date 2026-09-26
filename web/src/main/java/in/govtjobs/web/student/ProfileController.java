package in.govtjobs.web.student;

import in.govtjobs.domain.match.EligibilityFacts;
import in.govtjobs.web.store.StudentStore;
import in.govtjobs.web.support.IndiaStates;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProfileController {

    private final StudentStore students;
    private final CurrentStudent current;

    public ProfileController(StudentStore students, CurrentStudent current) {
        this.students = students;
        this.current = current;
    }

    @GetMapping("/profile")
    public String form(Principal principal, Model model) {
        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> stored = students.getProfile(current.requireId(principal));
        if (stored != null) {
            profile.putAll(stored);
        }
        model.addAttribute("nav", "profile");
        model.addAttribute("profile", profile);
        model.addAttribute("states", IndiaStates.STATES);
        model.addAttribute("complete", EligibilityFacts.validateMatchProfile(profile).ok());
        return "student/profile";
    }

    @PostMapping("/profile")
    public String save(
            Principal principal,
            @RequestParam String dob,
            @RequestParam String highestEducation,
            @RequestParam String reservationCategory,
            @RequestParam String birthState,
            @RequestParam(required = false) String educationDiscipline,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) List<String> domicileStates,
            @RequestParam(required = false) String pwbdHas,
            @RequestParam(required = false) String pwbdCategory,
            Model model) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("dob", dob);
        profile.put("highestEducation", highestEducation);
        profile.put("reservationCategory", reservationCategory);
        profile.put("birthState", birthState);
        profile.put("educationDiscipline", educationDiscipline);
        profile.put("gender", gender);
        profile.put("domicileStates", domicileStates == null ? List.of() : domicileStates);
        Map<String, Object> pwbd = new LinkedHashMap<>();
        pwbd.put("hasDisability", "yes".equals(pwbdHas));
        pwbd.put("category", pwbdCategory);
        profile.put("pwbd", pwbd);
        students.saveProfile(current.requireId(principal), profile);
        return "redirect:/profile?saved=1";
    }
}
