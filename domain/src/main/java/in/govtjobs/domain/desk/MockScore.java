package in.govtjobs.domain.desk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unofficial mock scoring. Never leak answers from publicBank.
 */
public final class MockScore {

    private MockScore() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> publicBank(Map<String, ?> bank) {
        if (bank == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seriesId", bank.get("seriesId"));
        out.put("unofficial", true);
        Object duration = bank.get("durationMin");
        double durationNum = 0;
        if (duration instanceof Number n) {
            durationNum = n.doubleValue();
        } else if (duration != null) {
            try {
                durationNum = Double.parseDouble(String.valueOf(duration));
            } catch (NumberFormatException ignored) {
                durationNum = 0;
            }
        }
        out.put("durationMin", durationNum > 0 ? durationNum : 20);

        List<Map<String, Object>> questions = new ArrayList<>();
        Object qs = bank.get("questions");
        if (qs instanceof Collection<?> col) {
            for (Object item : col) {
                if (!(item instanceof Map<?, ?> q)) {
                    continue;
                }
                Map<String, Object> pub = new LinkedHashMap<>();
                pub.put("id", q.get("id"));
                pub.put("stem", q.get("stem"));
                Object choices = q.get("choices");
                if (choices instanceof Collection<?> c) {
                    pub.put("choices", new ArrayList<>(c));
                } else {
                    pub.put("choices", List.of());
                }
                questions.add(pub);
            }
        }
        out.put("questions", questions);
        return out;
    }

    private static Integer chosenIndex(Map<String, ?> answers, Object questionId) {
        if (answers == null || questionId == null) {
            return null;
        }
        String key = String.valueOf(questionId);
        if (!answers.containsKey(key) && !(questionId instanceof String && answers.containsKey(questionId))) {
            // try exact key
            if (!answers.containsKey(questionId)) {
                return null;
            }
        }
        Object raw = answers.containsKey(questionId) ? answers.get(questionId) : answers.get(key);
        if (raw == null && !answers.containsKey(questionId) && !answers.containsKey(key)) {
            return null;
        }
        if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isNaN(d) && !Double.isInfinite(d)) {
                return (int) d;
            }
            return null;
        }
        try {
            double d = Double.parseDouble(String.valueOf(raw));
            if (d == Math.rint(d) && !Double.isNaN(d)) {
                return (int) d;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> scoreAttempt(Map<String, ?> bank, Map<String, ?> answers) {
        List<Map<String, Object>> questions = new ArrayList<>();
        if (bank != null && bank.get("questions") instanceof Collection<?> col) {
            for (Object item : col) {
                if (item instanceof Map<?, ?> q) {
                    questions.add((Map<String, Object>) q);
                }
            }
        }
        List<Object> correctIds = new ArrayList<>();
        List<Map<String, Object>> review = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            Integer chosen = chosenIndex(answers, q.get("id"));
            Object answerIndex = q.get("answerIndex");
            boolean ok = chosen != null && answerIndex != null && chosen.equals(toInt(answerIndex));
            if (ok) {
                correctIds.add(q.get("id"));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", q.get("id"));
            row.put("stem", q.get("stem"));
            Object choices = q.get("choices");
            if (choices instanceof Collection<?> c) {
                row.put("choices", new ArrayList<>(c));
            } else {
                row.put("choices", List.of());
            }
            row.put("chosen", chosen);
            row.put("answerIndex", q.get("answerIndex"));
            row.put("explain", q.get("explain"));
            row.put("ok", ok);
            review.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", correctIds.size());
        out.put("total", questions.size());
        out.put("correctIds", correctIds);
        out.put("review", review);
        return out;
    }

    private static Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
