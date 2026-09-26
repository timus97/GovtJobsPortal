package in.govtjobs.web;

import in.govtjobs.web.mail.StudentMailService;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.StudentStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JobStore jobs;
    private final StudentStore students;
    private final StudentMailService mail;

    public HealthController(JobStore jobs, StudentStore students, StudentMailService mail) {
        this.jobs = jobs;
        this.students = students;
        this.mail = mail;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> catalog = jobs.catalogHealth();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("service", "govt-jobs-portal");
        body.put("runtime", "java");
        body.put("time", Instant.now().toString());
        body.put("catalog", catalog);
        body.put("studentStore", Map.of("backend", students.backend()));
        body.put("mail", mail.provider());
        return body;
    }
}
