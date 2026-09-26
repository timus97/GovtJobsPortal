package in.govtjobs.domain.labels;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/** Display labels mirrored from client/src/utils/labels.js. */
public final class Labels {

    private Labels() {}

    public static final Map<String, String> ORG_TYPE_LABELS = Map.of(
            "central", "Central Govt",
            "psu", "PSU",
            "govt_company", "Govt Company",
            "autonomous", "Autonomous");

    public static final Map<String, String> SELECTION_LABELS = Map.ofEntries(
            Map.entry("walk_in", "Walk-in Interview"),
            Map.entry("interview_only", "Interview Only"),
            Map.entry("merit", "Merit Based"),
            Map.entry("contract_interview", "Contract + Interview"),
            Map.entry("direct_recruitment", "Direct Recruitment"),
            Map.entry("apprenticeship", "Apprenticeship"),
            Map.entry("cbt", "Computer-based test"),
            Map.entry("written_multi_stage", "Written (multi-stage)"),
            Map.entry("interview_after_exam", "Exam + interview"),
            Map.entry("physical", "Physical / PET-PST"));

    public static final Map<String, String> HAS_EXAM_LABELS = Map.of(
            "all", "All jobs",
            "yes", "Exam-based",
            "no", "No written exam");

    public static final Map<String, String> QUAL_LABELS = Map.ofEntries(
            Map.entry("below_10", "Below 10th"),
            Map.entry("10th", "10th"),
            Map.entry("12th", "12th"),
            Map.entry("iti", "ITI"),
            Map.entry("diploma", "Diploma"),
            Map.entry("graduate", "Graduate"),
            Map.entry("pg", "Postgraduate"),
            Map.entry("postgraduate", "Postgraduate"),
            Map.entry("phd", "PhD"),
            Map.entry("experience", "Experience-based"));

    public static final Map<String, String> STATUS_LABELS = Map.of(
            "open", "Open",
            "closing_soon", "Closing Soon",
            "closed", "Closed");

    private static final DateTimeFormatter DATE_IN =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("en-IN"));
    private static final DateTimeFormatter DATETIME_IN =
            DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.forLanguageTag("en-IN"));

    public static String formatDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return "—";
        }
        try {
            ZonedDateTime zdt = parseFlexible(iso);
            return DATE_IN.format(zdt);
        } catch (RuntimeException ex) {
            return iso;
        }
    }

    public static String formatDateTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "—";
        }
        try {
            ZonedDateTime zdt = parseFlexible(iso);
            return DATETIME_IN.format(zdt);
        } catch (RuntimeException ex) {
            return iso;
        }
    }

    private static ZonedDateTime parseFlexible(String iso) {
        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(iso.substring(0, Math.min(10, iso.length())))
                    .atStartOfDay(ZoneId.systemDefault());
        } catch (RuntimeException ex) {
            throw ex;
        }
    }
}
