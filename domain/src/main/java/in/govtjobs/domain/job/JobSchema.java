package in.govtjobs.domain.job;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Canonical job shape and helpers shared by process pipeline and API.
 */
public final class JobSchema {

    private JobSchema() {}

    public static final List<String> ORG_TYPES =
            List.of("central", "psu", "govt_company", "autonomous");

    public static final List<String> SELECTION_PROCESSES = List.of(
            "walk_in",
            "interview_only",
            "merit",
            "contract_interview",
            "direct_recruitment",
            "apprenticeship",
            "cbt",
            "written_multi_stage",
            "interview_after_exam",
            "physical");

    public static final List<String> STATUSES = List.of("open", "closing_soon", "closed");

    public static final List<String> QUALIFICATIONS = List.of(
            "below_10", "10th", "12th", "iti", "diploma", "graduate", "pg", "experience");

    private record PatternCode(String code, Pattern re) {}

    private static final List<PatternCode> EXAM_PATTERNS = List.of(
            new PatternCode(
                    "interview_after_exam",
                    Pattern.compile(
                            "interview\\s+after\\s+(?:a\\s+)?(?:written|cbt|computer\\s*[- ]?based|online\\s+(?:test|exam))",
                            Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "interview_after_exam",
                    Pattern.compile(
                            "(?:written(?:\\s+test)?|cbt|computer\\s*[- ]?based\\s+test).{0,48}followed\\s+by\\s+(?:a\\s+)?(?:personal\\s+)?interview",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL)),
            new PatternCode("cbt", Pattern.compile("\\bcbt\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "cbt",
                    Pattern.compile("computer\\s*[- ]?based\\s+test", Pattern.CASE_INSENSITIVE)),
            new PatternCode("cbt", Pattern.compile("online\\s+test", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "cbt", Pattern.compile("online\\s+examination", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("written\\s+test", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("written\\s+examination", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("competitive\\s+exam", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("tier[-\\s]?(i|ii|iii|1|2|3)\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("preliminary\\s+exam", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("mains\\s+examination", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("departmental\\s+competitive", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage", Pattern.compile("\\bgate\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("\\bnet\\b.*exam", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)),
            new PatternCode(
                    "written_multi_stage", Pattern.compile("\\bjrf\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage", Pattern.compile("\\bupsc\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage", Pattern.compile("\\bssc\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage", Pattern.compile("\\bibps\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "written_multi_stage",
                    Pattern.compile("rrb\\s+exam", Pattern.CASE_INSENSITIVE)),
            new PatternCode("physical", Pattern.compile("\\bpet\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode("physical", Pattern.compile("\\bpst\\b", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "physical",
                    Pattern.compile("physical\\s+standard", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "physical",
                    Pattern.compile("physical\\s+endurance", Pattern.CASE_INSENSITIVE)));

    public static final List<Pattern> EXCLUDE_PATTERNS =
            EXAM_PATTERNS.stream().map(PatternCode::re).toList();

    private static final List<PatternCode> INCLUDE_PATTERNS = List.of(
            new PatternCode("walk_in", Pattern.compile("walk[-\\s]?in", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "interview_only",
                    Pattern.compile(
                            "interview\\s+only|only\\s+interview|personal\\s+interview",
                            Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "merit",
                    Pattern.compile(
                            "purely\\s+on\\s+merit|no\\s+written\\s+test|without\\s+written",
                            Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "contract_interview",
                    Pattern.compile(
                            "contract.*interview|consultant.*interview|project\\s+staff",
                            Pattern.CASE_INSENSITIVE | Pattern.DOTALL)),
            new PatternCode(
                    "direct_recruitment",
                    Pattern.compile("direct\\s+recruitment", Pattern.CASE_INSENSITIVE)),
            new PatternCode(
                    "apprenticeship",
                    Pattern.compile("apprentice|apprentices\\s+act", Pattern.CASE_INSENSITIVE)));

    public record ClassificationResult(Boolean hasExam, String selectionProcess, String reason) {}

    public static ClassificationResult classifySelectionText(String text) {
        String blob = text == null ? "" : text;
        for (PatternCode p : EXAM_PATTERNS) {
            if (p.re().matcher(blob).find()) {
                return new ClassificationResult(true, p.code(), "exam_keyword");
            }
        }
        for (PatternCode p : INCLUDE_PATTERNS) {
            if (p.re().matcher(blob).find()) {
                return new ClassificationResult(false, p.code(), "include_keyword");
            }
        }
        return new ClassificationResult(null, null, "unknown");
    }

    public static List<String> isValidJob(Map<String, ?> job) {
        return isValidJob(job, false);
    }

    public static List<String> isValidJob(Map<String, ?> job, boolean allowNeedsReview) {
        if (job == null) {
            return List.of("not an object");
        }
        List<String> errors = new ArrayList<>();
        if (isBlank(job.get("id"))) {
            errors.add("id required");
        }
        if (isBlank(job.get("title"))) {
            errors.add("title required");
        }
        if (isBlank(job.get("organization"))) {
            errors.add("organization required");
        }
        String orgType = str(job.get("orgType"));
        if (!ORG_TYPES.contains(orgType)) {
            errors.add("invalid orgType");
        }
        String selectionProcess = str(job.get("selectionProcess"));
        if (!SELECTION_PROCESSES.contains(selectionProcess)) {
            errors.add("invalid selectionProcess");
        }
        Object hasExam = job.get("hasExam");
        if (!(hasExam instanceof Boolean)) {
            errors.add("hasExam must be boolean");
        }
        String officialUrl = str(job.get("officialUrl"));
        if (officialUrl.isEmpty() || !officialUrl.matches("(?i)^https?://.*")) {
            errors.add("officialUrl required");
        }
        if (isBlank(job.get("sourceId"))) {
            errors.add("sourceId required");
        }
        if (isBlank(job.get("sourceName"))) {
            errors.add("sourceName required");
        }
        if (isBlank(job.get("sourceUrl"))) {
            errors.add("sourceUrl required");
        }
        String status = str(job.get("status"));
        if (!STATUSES.contains(status)) {
            errors.add("invalid status");
        }
        if (Boolean.TRUE.equals(job.get("needsReview")) && !allowNeedsReview) {
            errors.add("needsReview not allowed in publish set");
        }
        return errors;
    }

    public static String computeStatus(String lastDate) {
        return computeStatus(lastDate, LocalDate.now(ZoneId.systemDefault()));
    }

    public static String computeStatus(String lastDate, LocalDate today) {
        if (lastDate == null || lastDate.isBlank()) {
            return "open";
        }
        LocalDate end = parseLocalDate(lastDate.trim());
        if (end == null) {
            return "open";
        }
        LocalDate t = today == null ? LocalDate.now(ZoneId.systemDefault()) : today;
        if (end.isBefore(t)) {
            return "closed";
        }
        long diffDays = end.toEpochDay() - t.toEpochDay();
        if (diffDays <= 7) {
            return "closing_soon";
        }
        return "open";
    }

    public static String stableJobId(Map<String, ?> parts) {
        if (parts == null) {
            return stableJobId("", "", "", "");
        }
        return stableJobId(
                str(parts.get("organization")),
                str(parts.get("title")),
                str(parts.get("lastDate")),
                str(parts.get("officialUrl")));
    }

    public static String stableJobId(
            String organization, String title, String lastDate, String officialUrl) {
        String raw = String.join(
                "|",
                lowerTrim(organization),
                lowerTrim(title),
                lowerTrim(lastDate == null ? "" : lastDate),
                lowerTrim(officialUrl == null ? "" : officialUrl));
        return sha256Hex16(raw);
    }

    static String sha256Hex16(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static LocalDate parseLocalDate(String value) {
        try {
            if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
                return LocalDate.parse(value.substring(0, 10));
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || str(value).isEmpty();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String lowerTrim(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }
}
