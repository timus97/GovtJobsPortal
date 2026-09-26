package in.govtjobs.domain.job;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Minimal Opportunity shape (Stage 0 stub).
 * selectionProcess stays a primary string; selectionProcesses[] is optional.
 */
public final class OpportunitySchema {

    private OpportunitySchema() {}

    public static List<String> isValidOpportunity(Map<String, ?> o) {
        if (o == null) {
            return List.of("not an object");
        }
        List<String> errors = new ArrayList<>();
        if (blank(o.get("id"))) {
            errors.add("id required");
        }
        if (blank(o.get("title"))) {
            errors.add("title required");
        }
        if (blank(o.get("organization"))) {
            errors.add("organization required");
        }
        String officialUrl = str(o.get("officialUrl"));
        if (officialUrl.isEmpty() || !officialUrl.matches("(?i)^https://.*")) {
            errors.add("officialUrl must be https");
        }
        if (!(o.get("hasExam") instanceof Boolean)) {
            errors.add("hasExam must be boolean");
        }
        String selectionProcess = str(o.get("selectionProcess"));
        if (!JobSchema.SELECTION_PROCESSES.contains(selectionProcess)) {
            errors.add("invalid selectionProcess");
        }
        if (o.containsKey("selectionProcesses") && o.get("selectionProcesses") != null) {
            Object sp = o.get("selectionProcesses");
            if (!(sp instanceof Collection<?> list)
                    || list.stream()
                            .anyMatch(code -> !JobSchema.SELECTION_PROCESSES.contains(str(code)))) {
                errors.add("invalid selectionProcesses");
            }
        }
        String status = str(o.get("status"));
        if (!JobSchema.STATUSES.contains(status)) {
            errors.add("invalid status");
        }
        return errors;
    }

    private static boolean blank(Object value) {
        return value == null || str(value).isEmpty();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
