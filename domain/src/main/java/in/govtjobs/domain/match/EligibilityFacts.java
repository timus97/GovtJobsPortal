package in.govtjobs.domain.match;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Structured eligibility facts + education ladder.
 * v1: map already-structured fields only. Do not NLP-parse eligibility[].
 */
public final class EligibilityFacts {

    private EligibilityFacts() {}

    public static final List<String> RESERVATION_CATEGORIES =
            List.of("UR", "EWS", "OBC", "SC", "ST");

    public static final List<String> DISCIPLINES =
            List.of("engineering", "commerce", "arts", "science", "law", "medical", "any");

    public static final List<String> GENDERS = List.of("male", "female", "other");

    public static final List<String> PWBD_CATEGORIES =
            List.of("VH", "HH", "OH", "others", "none");

    /** Shared ordinal ladder. pg and postgraduate are the same rank. experience is not a rung. */
    public static final Map<String, Integer> EDUCATION_ORDINAL = Map.ofEntries(
            Map.entry("below_10", 0),
            Map.entry("10th", 1),
            Map.entry("12th", 2),
            Map.entry("iti", 3),
            Map.entry("diploma", 4),
            Map.entry("graduate", 5),
            Map.entry("pg", 6),
            Map.entry("postgraduate", 6),
            Map.entry("phd", 7));

    public static final List<String> EDUCATION_CODES = List.of(
            "below_10",
            "10th",
            "12th",
            "iti",
            "diploma",
            "graduate",
            "pg",
            "postgraduate",
            "phd",
            "experience");

    public static final String PROFILE_STORAGE_KEY = "sarkari.profile.v1";

    public static final String VERIFY_OFFICIAL = "verify on official site";

    public record ValidationResult(boolean ok, List<String> errors) {}

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
                int y = Integer.parseInt(s.substring(0, 4));
                int mo = Integer.parseInt(s.substring(5, 7));
                int day = Integer.parseInt(s.substring(8, 10));
                if (mo < 1 || mo > 12 || day < 1 || day > 31) {
                    return null;
                }
                return LocalDate.of(y, mo, day);
            } catch (RuntimeException ex) {
                return null;
            }
        }
        try {
            return LocalDate.parse(s.substring(0, Math.min(10, s.length())));
        } catch (DateTimeParseException ex) {
            try {
                return InstantLike.toLocalDate(s);
            } catch (RuntimeException ex2) {
                return null;
            }
        }
    }

    public static String formatIsoDate(Object value) {
        LocalDate d = value instanceof LocalDate ld ? ld : parseIsoDate(value);
        if (d == null) {
            return null;
        }
        return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Completed years of age on asOn (notification date), not today.
     */
    public static Integer ageOnDate(Object dob, Object asOn) {
        LocalDate birth = parseIsoDate(dob);
        LocalDate on = parseIsoDate(asOn);
        if (birth == null || on == null) {
            return null;
        }
        int age = on.getYear() - birth.getYear();
        int monthDelta = on.getMonthValue() - birth.getMonthValue();
        if (monthDelta < 0
                || (monthDelta == 0 && on.getDayOfMonth() < birth.getDayOfMonth())) {
            age -= 1;
        }
        return age;
    }

    public static String normalizeQualification(Object code) {
        if (code == null) {
            return null;
        }
        String raw = String.valueOf(code).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        if (raw.isEmpty()) {
            return null;
        }
        if ("experience".equals(raw)) {
            return "experience";
        }
        if ("postgraduate".equals(raw) || "post_graduate".equals(raw) || "post-graduate".equals(raw)) {
            return "pg";
        }
        if (EDUCATION_ORDINAL.containsKey(raw)) {
            return raw;
        }
        return null;
    }

    public static boolean isEducationRung(Object code) {
        String n = normalizeQualification(code);
        return n != null && !"experience".equals(n);
    }

    public static Integer educationRank(Object code) {
        String n = normalizeQualification(code);
        if (!isEducationRung(n)) {
            return null;
        }
        return EDUCATION_ORDINAL.get(n);
    }

    public static String compareEducation(Object have, Object need) {
        String needNorm = normalizeQualification(need);
        if (needNorm == null || "experience".equals(needNorm)) {
            return "unknown";
        }
        String haveNorm = normalizeQualification(have);
        if (haveNorm == null || "experience".equals(haveNorm)) {
            return "unknown";
        }
        return EDUCATION_ORDINAL.get(haveNorm) >= EDUCATION_ORDINAL.get(needNorm) ? "pass" : "fail";
    }

    public static Double numberOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            double d = Double.parseDouble(s);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String normalizeCategory(Object value) {
        if (value == null) {
            return null;
        }
        String u = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (u.isEmpty()) {
            return null;
        }
        return RESERVATION_CATEGORIES.contains(u) ? u : null;
    }

    public static String normalizeDiscipline(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return null;
        }
        return DISCIPLINES.contains(raw) ? raw : null;
    }

    public static String normalizeGender(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return null;
        }
        return GENDERS.contains(raw) ? raw : null;
    }

    public static Map<String, Double> normalizeAgeRelaxation(Object map) {
        if (!(map instanceof Map<?, ?> m) || map instanceof List) {
            return null;
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (String cat : RESERVATION_CATEGORIES) {
            Object raw = m.get(cat);
            if (raw == null) {
                continue;
            }
            double years;
            if (raw instanceof Map<?, ?> nested) {
                Object y = nested.get("years");
                Double n = numberOrNull(y);
                if (n == null) {
                    continue;
                }
                years = n;
            } else {
                Double n = numberOrNull(raw);
                if (n == null) {
                    continue;
                }
                years = n;
            }
            if (years >= 0) {
                out.put(cat, years);
            }
        }
        return out.isEmpty() ? null : out;
    }

    public static Boolean boolOrNull(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            if (n.intValue() == 1) {
                return true;
            }
            if (n.intValue() == 0) {
                return false;
            }
        }
        if (value != null) {
            String s = String.valueOf(value);
            if ("true".equals(s) || "1".equals(s)) {
                return true;
            }
            if ("false".equals(s) || "0".equals(s)) {
                return false;
            }
        }
        return null;
    }

    public static List<String> asStringArray(Object value) {
        if (!(value instanceof Collection<?> col)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object s : col) {
            if (s == null) {
                continue;
            }
            String t = String.valueOf(s).trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static String normalizePwbdCategory(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        if ("others".equalsIgnoreCase(raw) || "other".equalsIgnoreCase(raw)) {
            return "others";
        }
        String u = raw.toUpperCase(Locale.ROOT);
        return Set.of("VH", "HH", "OH").contains(u) ? u : null;
    }

    public static List<String> normalizeOpenToCategories(Object value) {
        if (!(value instanceof Collection<?> col)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object item : col) {
            String cat = normalizeCategory(item);
            if (cat != null && !out.contains(cat)) {
                out.add(cat);
            }
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * Structured post list only. Missing pwbdAllowed stays null — never invent suitability.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> normalizePosts(Object posts) {
        if (!(posts instanceof Collection<?> col) || col.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int index = 0;
        for (Object item : col) {
            index += 1;
            if (!(item instanceof Map<?, ?> post)) {
                continue;
            }
            Map<String, Object> p = (Map<String, Object>) post;
            String title = firstNonBlank(p.get("title"), p.get("name"), p.get("post")).trim();
            if (title.isEmpty()) {
                continue;
            }
            List<String> cats = null;
            if (p.get("pwbdCategories") instanceof Collection<?> pc) {
                List<String> mapped = new ArrayList<>();
                for (Object c : pc) {
                    String n = normalizePwbdCategory(c);
                    if (n != null) {
                        mapped.add(n);
                    }
                }
                if (!mapped.isEmpty()) {
                    cats = mapped;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.get("id") != null ? p.get("id") : "post-" + index);
            row.put("title", title);
            row.put("pwbdAllowed", boolOrNull(p.get("pwbdAllowed")));
            row.put("pwbdCategories", cats);
            row.put("reservedOnly", boolOrNull(p.get("reservedOnly")));
            row.put("openToCategories", normalizeOpenToCategories(p.get("openToCategories")));
            out.add(row);
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * Copy structured facts from an Opportunity or compat job.
     * Never reads eligibility[] free text.
     */
    public static Map<String, Object> extractOpportunityFacts(Map<String, ?> opp) {
        Map<String, Object> empty = emptyFacts();
        if (opp == null) {
            return empty;
        }

        Object applicationClose = firstPresent(opp.get("applicationClose"), opp.get("lastDate"));
        Object applicationOpen = opp.get("applicationOpen");
        String minEducation =
                normalizeQualification(firstPresent(opp.get("minEducation"), opp.get("qualification")));
        Double ageMin = numberOrNull(opp.get("ageMin"));
        Double ageMax = numberOrNull(opp.get("ageMax"));
        String ageAsOnDate =
                opp.get("ageAsOnDate") != null ? formatIsoDate(opp.get("ageAsOnDate")) : null;
        Map<String, Double> ageRelaxation = normalizeAgeRelaxation(opp.get("ageRelaxation"));
        Object disciplineRaw = firstPresent(opp.get("disciplineRequired"), opp.get("discipline"));
        Object disciplineRequired;
        if (disciplineRaw == null || str(disciplineRaw).isEmpty()) {
            disciplineRequired = null;
        } else {
            String known = normalizeDiscipline(disciplineRaw);
            disciplineRequired = known != null ? known : String.valueOf(disciplineRaw);
        }
        String genderRequired = normalizeGender(opp.get("genderRequired"));
        boolean domicileRequired = Boolean.TRUE.equals(boolOrNull(opp.get("domicileRequired")));
        List<String> domicileStates = asStringArray(opp.get("domicileStates"));
        Boolean pwbdAllowed = boolOrNull(opp.get("pwbdAllowed"));
        List<String> pwbdCategories = null;
        if (opp.get("pwbdCategories") instanceof Collection<?> pc) {
            List<String> mapped = new ArrayList<>();
            for (Object c : pc) {
                String n = normalizePwbdCategory(c);
                if (n != null) {
                    mapped.add(n);
                }
            }
            if (!mapped.isEmpty()) {
                pwbdCategories = mapped;
            }
        }
        List<Map<String, Object>> posts = normalizePosts(opp.get("posts"));
        Boolean reservedOnly = boolOrNull(opp.get("reservedOnly"));
        List<String> openToCategories = normalizeOpenToCategories(opp.get("openToCategories"));

        boolean bandPresent = ageMin != null && ageMax != null;
        boolean complete = ageAsOnDate != null
                && bandPresent
                && minEducation != null
                && applicationClose != null
                && !str(applicationClose).isEmpty();

        boolean parseComplete = complete;
        Object ep = opp.get("eligibilityParse");
        if (ep instanceof Map<?, ?> epm && epm.get("complete") instanceof Boolean b) {
            parseComplete = b;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", opp.get("id"));
        out.put("title", opp.get("title"));
        out.put("organization", opp.get("organization"));
        out.put("officialUrl", firstPresent(opp.get("officialUrl"), opp.get("sourceUrl")));
        out.put("notificationUrl", firstPresent(opp.get("notificationUrl"), opp.get("officialUrl")));
        out.put("ageMin", ageMin);
        out.put("ageMax", ageMax);
        out.put("ageAsOnDate", ageAsOnDate);
        out.put("ageRelaxation", ageRelaxation);
        out.put("minEducation", minEducation);
        out.put("disciplineRequired", disciplineRequired);
        out.put("genderRequired", genderRequired);
        out.put("domicileRequired", domicileRequired);
        out.put("domicileStates", domicileStates);
        out.put("pwbdAllowed", pwbdAllowed);
        out.put("pwbdCategories", pwbdCategories);
        out.put("posts", posts);
        out.put("reservedOnly", reservedOnly);
        out.put("openToCategories", openToCategories);
        out.put("applicationOpen", applicationOpen);
        out.put("applicationClose", applicationClose);
        out.put("eligibilityParse", Map.of("complete", parseComplete));
        return out;
    }

    public static ValidationResult validateMatchProfile(Map<String, ?> profile) {
        if (profile == null) {
            return new ValidationResult(false, List.of("profile is required"));
        }
        List<String> errors = new ArrayList<>();
        if (normalizeCategory(profile.get("reservationCategory")) == null) {
            errors.add("reservationCategory is required (UR|EWS|OBC|SC|ST)");
        }
        if (parseIsoDate(profile.get("dob")) == null) {
            errors.add("dob is required");
        }
        if (normalizeQualification(profile.get("highestEducation")) == null) {
            errors.add("highestEducation is required");
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    private static Map<String, Object> emptyFacts() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", null);
        out.put("ageMin", null);
        out.put("ageMax", null);
        out.put("ageAsOnDate", null);
        out.put("ageRelaxation", null);
        out.put("minEducation", null);
        out.put("disciplineRequired", null);
        out.put("genderRequired", null);
        out.put("domicileRequired", false);
        out.put("domicileStates", List.of());
        out.put("pwbdAllowed", null);
        out.put("pwbdCategories", null);
        out.put("posts", null);
        out.put("reservedOnly", null);
        out.put("openToCategories", null);
        out.put("applicationOpen", null);
        out.put("applicationClose", null);
        out.put("officialUrl", null);
        out.put("notificationUrl", null);
        out.put("eligibilityParse", Map.of("complete", false));
        return out;
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v != null && !str(v).isEmpty()) {
                return str(v);
            }
        }
        return "";
    }

    private static Object firstPresent(Object... values) {
        for (Object v : values) {
            if (v != null && !str(v).isEmpty()) {
                return v;
            }
        }
        // preserve null lastDate semantics: if all null/empty, return first null-ish
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Fallback parser for non-ISO date strings. */
    private static final class InstantLike {
        private InstantLike() {}

        static LocalDate toLocalDate(String s) {
            try {
                return java.time.Instant.parse(s).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }
}
