package in.govtjobs.web;

import in.govtjobs.web.config.FeatureFlags;
import in.govtjobs.web.security.SessionCookies;
import in.govtjobs.web.store.PasswordResetService;
import in.govtjobs.web.store.StoreException;
import in.govtjobs.web.store.StudentStore;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final StudentStore students;
    private final FeatureFlags flags;
    private final SessionCookies cookies;
    private final PasswordResetService resets;

    public HomeController(
            StudentStore students, FeatureFlags flags, SessionCookies cookies, PasswordResetService resets) {
        this.students = students;
        this.flags = flags;
        this.cookies = cookies;
        this.resets = resets;
    }

    @GetMapping("/favicon.ico")
    public String favicon() {
        return "redirect:/favicon.svg";
    }

    @GetMapping({"/", "/account/login", "/account/register"})
    public String home(
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "next", required = false) String next,
            Model model) {
        boolean register = "register".equalsIgnoreCase(tab);
        model.addAttribute("register", register);
        model.addAttribute("next", next);
        model.addAttribute("notice", error != null ? "Email or password is not correct." : null);
        return "auth/landing";
    }

    @PostMapping("/account/register")
    public String register(
            @RequestParam String email,
            @RequestParam String password,
            HttpServletResponse response,
            Model model) {
        if (!flags.isStudent()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return "error";
        }
        try {
            var student = students.register(email, password);
            String studentId = String.valueOf(student.get("id"));
            cookies.setStudent(response, studentId, String.valueOf(student.get("email")), students.sessionEpoch(studentId));
            return "redirect:/profile";
        } catch (StoreException e) {
            model.addAttribute("register", true);
            model.addAttribute("notice", e.getMessage());
            return "auth/landing";
        }
    }

    @PostMapping("/account/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            HttpServletResponse response,
            Model model) {
        if (!flags.isStudent()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return "error";
        }
        var student = students.verify(email, password);
        if (student == null) {
            model.addAttribute("register", false);
            model.addAttribute("notice", "Email or password is not correct.");
            return "auth/landing";
        }
        cookies.setStudent(
                response,
                String.valueOf(student.get("id")),
                String.valueOf(student.get("email")),
                students.sessionEpoch(String.valueOf(student.get("id"))));
        return "redirect:/match";
    }

    @PostMapping("/account/logout")
    public String logout(HttpServletResponse response) {
        cookies.clearStudent(response);
        return "redirect:/";
    }

    @GetMapping("/account/forgot")
    public String forgotForm(Model model) {
        model.addAttribute("sent", false);
        return "auth/forgot";
    }

    @PostMapping("/account/forgot")
    public String forgot(@RequestParam String email, Model model) {
        Map<String, Object> result = resets.requestReset(email);
        model.addAttribute("sent", true);
        model.addAttribute("notice", result.get("message"));
        model.addAttribute("devResetUrl", result.get("devResetUrl"));
        model.addAttribute("devHint", result.get("devHint"));
        return "auth/forgot";
    }

    @GetMapping("/account/reset")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        boolean ok = token != null && resets.peek(token);
        model.addAttribute("token", token == null ? "" : token);
        model.addAttribute("valid", ok);
        if (!ok) {
            model.addAttribute("notice", "This reset link is invalid or has expired.");
        }
        return "auth/reset";
    }

    @PostMapping("/account/reset")
    public String reset(
            @RequestParam String token,
            @RequestParam String password,
            HttpServletResponse response,
            Model model) {
        try {
            Map<String, Object> student = resets.consume(token, password);
            String studentId = String.valueOf(student.get("id"));
            cookies.setStudent(response, studentId, String.valueOf(student.get("email")), students.sessionEpoch(studentId));
            return "redirect:/profile";
        } catch (StoreException e) {
            model.addAttribute("token", token);
            model.addAttribute("valid", false);
            model.addAttribute("notice", e.getMessage());
            return "auth/reset";
        }
    }

    @GetMapping("/ops/login")
    public String opsLogin(@RequestParam(name = "error", required = false) String error, Model model) {
        model.addAttribute("notice", error != null ? "Username or password is not correct." : null);
        return "auth/ops-login";
    }
}
