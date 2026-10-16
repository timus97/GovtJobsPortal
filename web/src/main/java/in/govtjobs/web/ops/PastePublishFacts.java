package in.govtjobs.web.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plain words for a paste row. The stored state can stay {@code published_local}. */
final class PastePublishFacts {

    static final String ON_SEARCH = "This notice is on Search.";
    static final String NOT_ON_SEARCH = "This notice is not on Search yet.";
    static final String REMOVED = "This notice was removed from Search.";
    static final String REJECTED = "This notice was rejected.";
    static final String CANNOT_CHECK =
            "Match cannot check age. This form does not ask for location or vacancies, so those stay unknown.";
    static final String SELECTION_REQUIRED = "Selection must be one of the filters students can use.";

    private PastePublishFacts() {}

    static String sentence(Object state) {
        String value = text(state);
        return switch (value) {
            case "published_local", "published" -> ON_SEARCH;
            case "unpublished" -> REMOVED;
            case "rejected" -> REJECTED;
            default -> NOT_ON_SEARCH;
        };
    }

    static String tableLabel(Object state) {
        String value = text(state);
        return switch (value) {
            case "published_local", "published" -> "On Search";
            case "unpublished" -> "Removed from Search";
            case "rejected" -> "Rejected";
            case "needs_review" -> "Needs review";
            default -> "Not on Search";
        };
    }

    static String searchPath(Object state, Object opportunityId) {
        String value = text(state);
        if (!"published_local".equals(value) && !"published".equals(value)) {
            return "";
        }
        String id = text(opportunityId);
        if (id.isEmpty() || !id.matches("[A-Za-z0-9._-]+")) {
            return "";
        }
        return "/jobs/" + id;
    }

    static List<String> canCheck(Map<String, Object> extracted) {
        Map<String, Object> facts = extracted == null ? Map.of() : extracted;
        List<String> lines = new ArrayList<>();
        lines.add("Title: " + stated(facts.get("title")));
        lines.add("Organisation: " + stated(facts.get("organization")));
        lines.add("Last date: " + stated(facts.get("lastDate")));
        lines.add("Exam: " + (Boolean.TRUE.equals(facts.get("hasExam")) ? "Has an exam" : "No exam is marked"));
        return lines;
    }

    static List<String> leftBlank(Map<String, Object> extracted) {
        Map<String, Object> facts = extracted == null ? Map.of() : extracted;
        List<String> blank = new ArrayList<>();
        if (text(facts.get("title")).isEmpty()) {
            blank.add("title");
        }
        if (text(facts.get("organization")).isEmpty()) {
            blank.add("organisation");
        }
        if (text(facts.get("lastDate")).isEmpty()) {
            blank.add("last date");
        }
        if (text(facts.get("summary")).isEmpty()) {
            blank.add("summary");
        }
        if (text(facts.get("selectionProcess")).isEmpty()) {
            blank.add("selection");
        }
        return blank;
    }

    private static String stated(Object value) {
        String text = text(value);
        return text.isEmpty() ? "not stated" : text;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return "null".equals(text) ? "" : text;
    }
}
