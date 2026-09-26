package in.govtjobs.domain.desk;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unofficial even-split study plan. Never invents topics or exam dates.
 */
public final class StudyPlan {

    private StudyPlan() {}

    public static final String ADD_DATE_NOTE = "Add exam date to split the plan.";

    public static double topicWeight(Map<String, ?> topic) {
        if (topic == null) {
            return 1;
        }
        Object w = topic.get("weight");
        if (w instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) && d > 0 ? d : 1;
        }
        if (w != null) {
            try {
                double d = Double.parseDouble(String.valueOf(w));
                return Double.isFinite(d) && d > 0 ? d : 1;
            } catch (NumberFormatException ex) {
                return 1;
            }
        }
        return 1;
    }

    public static String addUtcDays(Object isoOrDate, int n) {
        LocalDate d;
        if (isoOrDate instanceof LocalDate ld) {
            d = ld;
        } else {
            d = DeskGuidance.parseIsoDate(isoOrDate);
        }
        if (d == null) {
            return null;
        }
        return DeskGuidance.formatIsoDate(d.plusDays(n));
    }

    static int[] splitDaysByWeight(List<? extends Map<String, ?>> topics, int totalDays) {
        int size = topics.size();
        double[] weights = new double[size];
        double totalWeight = 0;
        for (int i = 0; i < size; i++) {
            weights[i] = topicWeight(topics.get(i));
            totalWeight += weights[i];
        }
        if (totalWeight == 0) {
            totalWeight = size == 0 ? 1 : size;
        }
        double[] raw = new double[size];
        int[] alloc = new int[size];
        int sum = 0;
        for (int i = 0; i < size; i++) {
            raw[i] = (totalDays * weights[i]) / totalWeight;
            alloc[i] = (int) Math.floor(raw[i]);
            sum += alloc[i];
        }
        int leftover = totalDays - sum;
        List<int[]> order = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            order.add(new int[] {i, /* frac marker via separate array */ i});
        }
        order.sort(Comparator.<int[]>comparingDouble(a -> -(raw[a[0]] - Math.floor(raw[a[0]])))
                .thenComparingInt(a -> a[0]));
        for (int k = 0; leftover > 0 && k < order.size(); k++) {
            alloc[order.get(k)[0]] += 1;
            leftover -= 1;
        }
        return alloc;
    }

    private static List<Map<String, Object>> nullRangeTopics(List<? extends Map<String, ?>> topics) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, ?> topic : topics) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (topic != null) {
                row.putAll(topic);
            }
            row.put("weight", topicWeight(topic));
            row.put("startDate", null);
            row.put("endDate", null);
            row.put("days", null);
            out.add(row);
        }
        return out;
    }

    /**
     * Split remaining UTC days (today..exam inclusive) across topics by weight.
     * daysLeft &lt;= 0 or missing date → null ranges + add-exam-date note.
     * Fewer days than topics → later topics share the last day.
     */
    public static Map<String, Object> buildPlan(
            List<? extends Map<String, ?>> topics, Object examDate, Object today) {
        List<? extends Map<String, ?>> list = topics == null ? List.of() : topics;
        LocalDate now;
        if (today instanceof LocalDate ld) {
            now = ld;
        } else if (today instanceof java.util.Date util) {
            now = util.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        } else {
            LocalDate parsed = DeskGuidance.parseIsoDate(today);
            now = parsed != null ? parsed : LocalDate.now(ZoneOffset.UTC);
        }

        Integer left = DeskGuidance.daysLeft(examDate, now);

        if (left == null || left <= 0) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("unofficial", true);
            out.put("days", left);
            out.put("note", ADD_DATE_NOTE);
            out.put("topics", nullRangeTopics(list));
            return out;
        }

        int totalDays = left + 1;
        String todayIso = DeskGuidance.formatIsoDate(now);
        String examIso = DeskGuidance.formatIsoDate(examDate);
        int[] alloc = splitDaysByWeight(list, totalDays);
        int offset = 0;

        List<Map<String, Object>> planned = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Map<String, ?> topic = list.get(i);
            int n = i < alloc.length ? alloc[i] : 0;
            Map<String, Object> row = new LinkedHashMap<>();
            if (topic != null) {
                row.putAll(topic);
            }
            row.put("weight", topicWeight(topic));
            if (n <= 0) {
                row.put("startDate", examIso);
                row.put("endDate", examIso);
                row.put("days", 1);
            } else {
                row.put("startDate", addUtcDays(todayIso, offset));
                row.put("endDate", addUtcDays(todayIso, offset + n - 1));
                row.put("days", n);
                offset += n;
            }
            planned.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unofficial", true);
        out.put("days", totalDays);
        out.put("topics", planned);
        return out;
    }
}
