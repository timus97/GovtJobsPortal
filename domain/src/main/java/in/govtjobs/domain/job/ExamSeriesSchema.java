package in.govtjobs.domain.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ExamSeries: official calendar / prepare-for rows.
 * Not a vacancy. Apply is allowed only via a linked open Opportunity.
 */
public final class ExamSeriesSchema {

    private ExamSeriesSchema() {}

    public static final List<String> BOARDS =
            List.of("UPSC", "SSC", "IBPS", "SBI", "RRB", "NTA");

    public static final Pattern CUET_RE = Pattern.compile("\\bcuet\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NET_RE =
            Pattern.compile("\\bugc\\s*net\\b|\\bnta\\s*net\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern TITLE_NOISE = Pattern.compile(
            "\\b(examination|exam|recruitment|notification|advertisement|common recruitment process|crp)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIXTURE = Pattern.compile("\\(fixture\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern CALENDAR_SOURCE = Pattern.compile("_calendar$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALENDAR_VERSION = Pattern.compile("calendar", Pattern.CASE_INSENSITIVE);

    private static final Map<String, Pattern> BOARD_ORG_RE = Map.of(
            "upsc", Pattern.compile("union public service|\\bupsc\\b", Pattern.CASE_INSENSITIVE),
            "ssc", Pattern.compile("staff selection|\\bssc\\b", Pattern.CASE_INSENSITIVE),
            "ibps", Pattern.compile("\\bibps\\b|banking personnel", Pattern.CASE_INSENSITIVE),
            "sbi", Pattern.compile("state bank|\\bsbi\\b", Pattern.CASE_INSENSITIVE),
            "rrb", Pattern.compile("railway recruitment|\\brrb\\b", Pattern.CASE_INSENSITIVE),
            "nta", Pattern.compile("\\bnta\\b|ugc\\s*net", Pattern.CASE_INSENSITIVE));

    public static boolean isCuetName(Object name) {
        return CUET_RE.matcher(String.valueOf(name == null ? "" : name)).find();
    }

    public static boolean isNetName(Object name) {
        return NET_RE.matcher(String.valueOf(name == null ? "" : name)).find();
    }

    public static String stableSeriesId(Map<String, ?> parts) {
        String board = parts == null ? "" : str(parts.get("board"));
        String name = parts == null ? "" : str(parts.get("name"));
        String cycle = parts == null ? "" : str(parts.get("cycle"));
        String raw = String.join(
                "|",
                board.toLowerCase(Locale.ROOT).trim(),
                name.toLowerCase(Locale.ROOT).trim(),
                cycle.toLowerCase(Locale.ROOT).trim());
        return JobSchema.sha256Hex16(raw);
    }

    public static String normalizeTitle(Object value) {
        String s = String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT);
        s = FIXTURE.matcher(s).replaceAll(" ");
        s = TITLE_NOISE.matcher(s).replaceAll(" ");
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        s = SPACES.matcher(s).replaceAll(" ").trim();
        return s;
    }

    public static List<String> isValidExamSeries(Map<String, ?> s) {
        if (s == null) {
            return List.of("not an object");
        }
        List<String> errors = new ArrayList<>();
        if (blank(s.get("id"))) {
            errors.add("id required");
        }
        if (blank(s.get("board"))) {
            errors.add("board required");
        }
        if (blank(s.get("name"))) {
            errors.add("name required");
        }
        if (isCuetName(s.get("name"))) {
            errors.add("CUET is excluded");
        }
        String officialUrl = str(s.get("officialUrl"));
        if (officialUrl.isEmpty() || !officialUrl.matches("(?i)^https://.*")) {
            errors.add("officialUrl must be https");
        }
        Object kind = s.get("kind");
        if (kind != null && !"series".equals(str(kind))) {
            errors.add("kind must be series");
        }
        Object minEdu = s.get("minEducation");
        if (minEdu != null
                && !blank(minEdu)
                && !JobSchema.QUALIFICATIONS.contains(str(minEdu))
                && !"phd".equals(str(minEdu))
                && !"postgraduate".equals(str(minEdu))) {
            errors.add("invalid minEducation");
        }
        return errors;
    }

    public static Map<String, Object> normalizeExamSeries(Map<String, ?> raw) {
        return normalizeExamSeries(raw, Instant.now().toString());
    }

    public static Map<String, Object> normalizeExamSeries(Map<String, ?> raw, String now) {
        if (raw == null) {
            return null;
        }
        if (isCuetName(raw.get("name"))) {
            return null;
        }
        String board = str(raw.get("board")).trim();
        String name = str(raw.get("name")).trim();
        String officialUrl = firstNonBlank(raw.get("officialUrl"), raw.get("official_url"));
        if (board.isEmpty() || name.isEmpty() || !officialUrl.matches("(?i)^https://.*")) {
            return null;
        }
        boolean applyNever = Boolean.TRUE.equals(raw.get("applyNever")) || isNetName(name);
        String id = blank(raw.get("id"))
                ? stableSeriesId(Map.of("board", board, "name", name, "cycle", str(raw.get("cycle"))))
                : str(raw.get("id"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("kind", "series");
        out.put("board", board);
        out.put("name", name);
        out.put("cycle", blank(raw.get("cycle")) ? null : raw.get("cycle"));
        out.put(
                "sourceId",
                firstPresent(raw.get("sourceId"), raw.get("source_id")));
        out.put("officialUrl", officialUrl);
        out.put(
                "expectedNotify",
                firstPresent(
                        raw.get("expectedNotify"),
                        raw.get("expected_notify"),
                        raw.get("notificationDate")));
        out.put(
                "expectedApply",
                firstPresent(
                        raw.get("expectedApply"),
                        raw.get("expected_apply"),
                        raw.get("lastDate")));
        out.put(
                "expectedExam",
                firstPresent(
                        raw.get("expectedExam"),
                        raw.get("expected_exam"),
                        raw.get("examDate")));
        out.put(
                "minEducation",
                firstPresent(raw.get("minEducation"), raw.get("min_education")));
        out.put("applyNever", applyNever);
        out.put("aliases", asStringList(raw.get("aliases")));
        out.put("linkedOpportunityIds", asStringList(raw.get("linkedOpportunityIds")));
        out.put("updatedAt", blank(raw.get("updatedAt")) ? now : raw.get("updatedAt"));
        return out;
    }

    public static boolean boardMatchesJob(Map<String, ?> series, Map<String, ?> job) {
        String board = str(series == null ? null : series.get("board")).toLowerCase(Locale.ROOT);
        if (board.isEmpty()) {
            return true;
        }
        String blob = (str(job.get("organization"))
                        + " "
                        + str(job.get("sourceId"))
                        + " "
                        + str(job.get("sourceName")))
                .toLowerCase(Locale.ROOT);
        if (blob.contains(board)) {
            return true;
        }
        Pattern re = BOARD_ORG_RE.get(board);
        return re == null || re.matcher(blob).find();
    }

    public static boolean seriesMatchesJob(Map<String, ?> series, Map<String, ?> job) {
        if (series == null || job == null || Boolean.TRUE.equals(series.get("applyNever"))) {
            return false;
        }
        String jobTitle = normalizeTitle(job.get("title"));
        if (jobTitle.isEmpty()) {
            return false;
        }
        List<String> names = new ArrayList<>();
        names.add(normalizeTitle(series.get("name")));
        Object aliases = series.get("aliases");
        if (aliases instanceof Collection<?> col) {
            for (Object a : col) {
                names.add(normalizeTitle(a));
            }
        }
        boolean titleOk = names.stream()
                .filter(n -> n != null && !n.isEmpty())
                .anyMatch(n -> jobTitle.contains(n) || n.contains(jobTitle));
        if (!titleOk) {
            return false;
        }
        return boardMatchesJob(series, job);
    }

    public static boolean looksLikeCalendarRow(Map<String, ?> raw) {
        if (raw == null) {
            return false;
        }
        if (isCuetName(firstPresent(raw.get("title"), raw.get("name")))) {
            return true;
        }
        String sourceId = str(raw.get("sourceId"));
        String version = str(raw.get("collectorVersion"));
        if (CALENDAR_SOURCE.matcher(sourceId).find()) {
            return true;
        }
        if (CALENDAR_VERSION.matcher(version).find()) {
            return true;
        }
        if (raw.get("examDate") != null
                && Boolean.TRUE.equals(raw.get("hasExam"))
                && blank(raw.get("lastDate"))) {
            return true;
        }
        return false;
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof Collection<?> col)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : col) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    private static Object firstPresent(Object... values) {
        for (Object v : values) {
            if (v != null && !str(v).isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private static String firstNonBlank(Object a, Object b) {
        if (!blank(a)) {
            return str(a);
        }
        if (!blank(b)) {
            return str(b);
        }
        return "";
    }

    private static boolean blank(Object value) {
        return value == null || str(value).isEmpty();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
