package in.govtjobs.web.student;

import in.govtjobs.web.store.StoreException;
import in.govtjobs.web.store.StudentStore;
import java.security.Principal;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CurrentStudent {

    private final StudentStore students;

    public CurrentStudent(StudentStore students) {
        this.students = students;
    }

    public String requireId(Principal principal) {
        Map<String, Object> row = requireRow(principal);
        return String.valueOf(row.get("id"));
    }

    public Map<String, Object> requireRow(Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication auth
                && auth.getDetails() instanceof String id
                && !id.isBlank()) {
            Map<String, Object> byId = students.findInternalById(id);
            if (byId != null) {
                return byId;
            }
        }
        if (principal == null || principal.getName() == null) {
            throw new StoreException("AUTH", "Not signed in");
        }
        Map<String, Object> row = students.findInternalByEmail(principal.getName());
        if (row == null) {
            throw new StoreException("AUTH", "Session has no student account");
        }
        return row;
    }
}
