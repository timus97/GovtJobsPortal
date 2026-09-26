package in.govtjobs.domain.desk;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Desk tracker: kinds, statuses, days-left, next-step copy.
 * Never invent dates from free text.
 */
public final class DeskGuidance {

    private DeskGuidance() {}

    public static final List<String> KINDS = List.of("series", "opportunity", "custom");
    public static final List<String> STATUSES =
            List.of("watching", "applied", "admit_ready", "appeared", "result_in", "done");

    public static final Map<String, String> STATUS_LABELS = Map.of(
            "watching", "Watching",
            "applied", "Applied",
            "admit_ready", "Admit ready",
            "appeared", "Appeared",
            "result_in", "Result in",
            "done", "Done");

    public static final Map<String, String> KIND_LABELS = Map.of(
            "series", "Calendar",
            "opportunity", "Job applied",
            "custom", "Custom");

    public static LocalDate parseIsoDate(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            try {
                return LocalDate.of(
                        Integer.parseInt(s.substring(0, 4)),
                        Integer.parseInt(s.substring(5, 7)),
                        Integer.parseInt(s.substring(8, 10)));
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    public static String formatIsoDate(Object value) {
        LocalDate d = value instanceof LocalDate ld ? ld : parseIsoDate(value);
        if (d == null) {
            return null;
        }
        return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Whole UTC days from today to anchor. Negative = days ago. Null if no date.
     */
    public static Integer daysLeft(Object anchor, Object today) {
        LocalDate end = parseIsoDate(anchor);
        if (end == null) {
            return null;
        }
        LocalDate now;
        if (today instanceof LocalDate ld) {
            now = ld;
        } else if (today instanceof java.util.Date util) {
            now = util.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        } else if (today != null) {
            LocalDate parsed = parseIsoDate(today);
            now = parsed != null ? parsed : LocalDate.now(ZoneOffset.UTC);
        } else {
            now = LocalDate.now(ZoneOffset.UTC);
        }
        long diff = end.toEpochDay() - now.toEpochDay();
        return Math.toIntExact(diff);
    }

    public static Integer daysLeft(Object anchor) {
        return daysLeft(anchor, LocalDate.now(ZoneOffset.UTC));
    }

    public static Object pickAnchor(Map<String, ?> item) {
        if (item == null) {
            return null;
        }
        Object exam = item.get("examDate");
        if (exam != null && !str(exam).isEmpty()) {
            return exam;
        }
        Object last = item.get("lastDate");
        return last == null || str(last).isEmpty() ? null : last;
    }

    public static Map<String, String> daysLeftLabel(Integer days) {
        if (days == null) {
            return Map.of("number", "—", "caption", "Add exam date");
        }
        if (days == 0) {
            return Map.of("number", "0", "caption", "today");
        }
        if (days > 0) {
            return Map.of(
                    "number",
                    String.valueOf(days),
                    "caption",
                    days == 1 ? "day left" : "days left");
        }
        int ago = Math.abs(days);
        return Map.of(
                "number",
                String.valueOf(ago),
                "caption",
                ago == 1 ? "day ago" : "days ago");
    }

    public static String nextStep(Map<String, ?> item) {
        Integer days = item.get("daysLeft") instanceof Number n ? n.intValue() : null;
        boolean hasAdmit = truthy(item.get("hasAdmit"));
        boolean hasResult = truthy(item.get("hasResult"));
        String status = item.get("status") == null ? "watching" : str(item.get("status"));

        if ("watching".equals(status) && truthy(item.get("applyOpen"))) {
            return "Apply on the official site, then mark Applied.";
        }
        if ("watching".equals(status)) {
            return "Prepare — exam date or notification is not open yet.";
        }
        if ("applied".equals(status) && !hasAdmit) {
            return "Upload the admit card when the board releases it.";
        }
        if (("admit_ready".equals(status) || hasAdmit) && days != null && days > 0) {
            return "Exam in " + days + " day" + (days == 1 ? "" : "s") + " — follow your plan.";
        }
        if ("admit_ready".equals(status) && (days == null || days <= 0)) {
            return "Admit card is on file. Sit the paper, then mark Appeared.";
        }
        if ("appeared".equals(status) && !hasResult) {
            return "Upload the result when it is declared.";
        }
        if ("result_in".equals(status) || "done".equals(status)) {
            return "Review your score. Keep documents private on this host.";
        }
        return "Open the desk to update status or dates.";
    }

    public static Map<String, Object> decorateItem(Map<String, ?> raw) {
        return decorateItem(raw, Map.of());
    }

    public static Map<String, Object> decorateItem(Map<String, ?> raw, Map<String, ?> extras) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (raw != null) {
            item.putAll(raw);
        }
        if (extras != null) {
            item.putAll(extras);
        }
        Object anchor = pickAnchor(item);
        Integer days = daysLeft(anchor);
        Map<String, String> label = daysLeftLabel(days);
        item.put("anchorDate", formatIsoDate(anchor));
        item.put("daysLeft", days);
        item.put("daysNumber", label.get("number"));
        item.put("daysCaption", label.get("caption"));
        String kind = str(item.get("kind"));
        String status = str(item.get("status"));
        item.put("kindLabel", KIND_LABELS.getOrDefault(kind, kind));
        item.put("statusLabel", STATUS_LABELS.getOrDefault(status, status));
        item.put("nextStep", nextStep(item));
        return item;
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        String s = String.valueOf(value);
        return !s.isEmpty() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
