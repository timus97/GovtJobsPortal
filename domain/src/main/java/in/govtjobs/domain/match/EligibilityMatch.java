package in.govtjobs.domain.match;

import in.govtjobs.domain.job.JobSchema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class EligibilityMatch {

    public static final double LOW_CONFIDENCE = 0.6;
    public static final String LAST_DATE_UNKNOWN_CHIP = "Last date not listed — verify on official site";
    public static final String VERIFY_BADGE = "Low confidence — verify on official site";
    public static final String AGE_WHEN_NOTIFIED = "Age will be computed when notification is out";

    private EligibilityMatch() {}

    public static Map<String, Object> reason(String rule, String outcome, String detail) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rule", rule);
        r.put("outcome", outcome);
        r.put("detail", detail);
        return r;
    }

    public static void requireMatchableProfile(Map<String, Object> profile) {
        EligibilityFacts.ValidationResult v = EligibilityFacts.validateMatchProfile(profile);
        if (!v.ok()) {
            throw new MatchException(v.errors());
        }
    }

    public static Map<String, List<Map<String, Object>>> matchOpportunities(
            Map<String, Object> profile, List<Map<String, Object>> opportunities) {
        requireMatchableProfile(profile);
        List<Map<String, Object>> list = opportunities == null ? List.of() : opportunities;
        List<Map<String, Object>> scored = list.stream().map(opp -> matchOne(profile, opp)).toList();
        List<Map<String, Object>> matches = rankMatches(scored.stream()
                        .filter(r -> ((Number) r.get("fails")).intValue() == 0)
                        .toList())
                .stream()
                .map(EligibilityMatch::stripInternal)
                .toList();
        List<Map<String, Object>> excluded = scored.stream()
                .filter(r -> ((Number) r.get("fails")).intValue() > 0)
                .map(EligibilityMatch::stripInternal)
                .toList();
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        out.put("matches", matches);
        out.put("excluded", excluded);
        return out;
    }

    public static Map<String, List<Map<String, Object>>> matchExamSeries(
            Map<String, Object> profile, List<Map<String, Object>> seriesList) {
        requireMatchableProfile(profile);
        List<Map<String, Object>> list = seriesList == null ? List.of() : seriesList;
        List<Map<String, Object>> scored = list.stream().map(s -> matchOneSeries(profile, s)).toList();
        List<Map<String, Object>> matches = scored.stream()
                .filter(r -> ((Number) r.get("fails")).intValue() == 0)
                .sorted(Comparator.comparingDouble(r -> -((Number) r.get("score")).doubleValue()))
                .toList();
        List<Map<String, Object>> excluded =
                scored.stream().filter(r -> ((Number) r.get("fails")).intValue() > 0).toList();
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        out.put("matches", matches);
        out.put("excluded", excluded);
        return out;
    }

    public static Map<String, Object> matchOne(Map<String, Object> profile, Map<String, Object> opportunity) {
        Map<String, Object> extracted = EligibilityFacts.extractOpportunityFacts(opportunity);
        List<Map<String, Object>> reasons = List.of(
                evaluateStatus(extracted),
                evaluateAge(profile, extracted),
                evaluateEducation(profile, extracted),
                evaluateDiscipline(profile, extracted),
                evaluateDomicile(profile, extracted),
                evaluateGender(profile, extracted),
                evaluatePwbd(profile, extracted),
                evaluateCategory(profile, extracted));
        int applicable = reasons.size();
        int covered = (int) reasons.stream().filter(r -> !"unknown".equals(r.get("outcome"))).count();
        int fails = (int) reasons.stream().filter(r -> "fail".equals(r.get("outcome"))).count();
        double confidence = applicable == 0 ? 0 : Math.round((covered / (double) applicable) * 10000d) / 10000d;
        double score = Math.round(100 * confidence * (fails == 0 ? 1 : 0) * 100d) / 100d;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", first(opportunity.get("id"), extracted.get("id")));
        row.put("title", first(opportunity.get("title"), extracted.get("title")));
        row.put("organization", first(opportunity.get("organization"), extracted.get("organization")));
        row.put("officialUrl", first(opportunity.get("officialUrl"), extracted.get("officialUrl")));
        row.put("lastDate", first(opportunity.get("lastDate"), extracted.get("applicationClose")));
        row.put("applicationClose", extracted.get("applicationClose"));
        row.put("score", score);
        row.put("confidence", confidence);
        row.put("reasons", reasons);
        row.put("fails", fails);
        row.put("lowConfidence", fails == 0 && confidence < LOW_CONFIDENCE);
        row.put("_closeSortKey", closeSortKey(extracted));
        return row;
    }

    public static Map<String, Object> matchOneSeries(Map<String, Object> profile, Map<String, Object> series) {
        List<Map<String, Object>> reasons = new ArrayList<>();
        Object minEducation = first(series.get("minEducation"), series.get("min_education"));
        if (minEducation == null) {
            reasons.add(reason(
                    "education", "unknown", "Typical education floor not listed — verify when notification is out"));
        } else {
            String cmp = EligibilityFacts.compareEducation(profile.get("highestEducation"), minEducation);
            if ("pass".equals(cmp)) {
                reasons.add(reason("education", "pass", "Education meets typical " + minEducation + " floor"));
            } else if ("fail".equals(cmp)) {
                reasons.add(reason("education", "fail", "Typical floor is " + minEducation));
            } else {
                reasons.add(reason(
                        "education",
                        "unknown",
                        "Typical education floor not listed — verify when notification is out"));
            }
        }
        if (series.get("ageAsOnDate") == null || series.get("ageMin") == null || series.get("ageMax") == null) {
            reasons.add(reason("age", "unknown", AGE_WHEN_NOTIFIED));
        } else {
            reasons.add(evaluateAge(profile, series));
        }
        List<?> openIds = series.get("linkedOpportunityIds") instanceof List<?> l ? l : List.of();
        if (Boolean.TRUE.equals(series.get("applyNever"))) {
            reasons.add(reason("apply", "unknown", "Prepare-for only — not a vacancy"));
        } else if (!openIds.isEmpty()) {
            reasons.add(reason("apply", "pass", "A linked apply window is open"));
        } else {
            reasons.add(reason("apply", "unknown", "No open apply window yet — start preparing"));
        }
        int fails = (int) reasons.stream().filter(r -> "fail".equals(r.get("outcome"))).count();
        int covered = (int) reasons.stream().filter(r -> !"unknown".equals(r.get("outcome"))).count();
        double confidence = reasons.isEmpty() ? 0 : Math.round((covered / (double) reasons.size()) * 10000d) / 10000d;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", series.get("id"));
        row.put("title", series.get("name"));
        row.put("board", series.get("board"));
        row.put("officialUrl", series.get("officialUrl"));
        row.put("cycle", series.get("cycle"));
        row.put("applyNever", Boolean.TRUE.equals(series.get("applyNever")));
        row.put("canApply", !Boolean.TRUE.equals(series.get("applyNever")) && !openIds.isEmpty());
        row.put("linkedOpportunityIds", openIds);
        row.put("score", Math.round(100 * confidence * (fails == 0 ? 1 : 0) * 100d) / 100d);
        row.put("confidence", confidence);
        row.put("reasons", reasons);
        row.put("fails", fails);
        return row;
    }

    static Map<String, Object> evaluateStatus(Map<String, Object> extracted) {
        Object close = extracted.get("applicationClose");
        if (close == null || String.valueOf(close).isBlank()) {
            return reason("status", "unknown", LAST_DATE_UNKNOWN_CHIP);
        }
        String status = JobSchema.computeStatus(String.valueOf(close));
        if ("closed".equals(status)) {
            return reason("status", "fail", "Application closed on " + close);
        }
        if ("closing_soon".equals(status)) {
            return reason("status", "pass", "Last date " + close + " (closing soon)");
        }
        return reason("status", "pass", "Last date " + close);
    }

    static Map<String, Object> evaluateAge(Map<String, Object> profile, Map<String, Object> extracted) {
        Double bandMin = EligibilityFacts.numberOrNull(extracted.get("ageMin"));
        Double bandMax = EligibilityFacts.numberOrNull(extracted.get("ageMax"));
        Object asOn = extracted.get("ageAsOnDate");
        if (asOn == null || bandMin == null || bandMax == null) {
            return reason("age", "unknown", "Age band or as-on date not listed — verify on official site");
        }
        Integer age = EligibilityFacts.ageOnDate(profile.get("dob"), asOn);
        if (age == null) {
            return reason("age", "unknown", "Age band or as-on date not listed — verify on official site");
        }
        String category = EligibilityFacts.normalizeCategory(profile.get("reservationCategory"));
        Object printed = extracted.get("ageRelaxation");
        double extra = 0;
        if (printed instanceof Map<?, ?> map && category != null && map.get(category) != null) {
            Double n = EligibilityFacts.numberOrNull(map.get(category));
            extra = n == null ? 0 : n;
        }
        boolean applied = Double.isFinite(extra) && extra > 0;
        double effectiveMax = bandMax + (applied ? extra : 0);
        boolean inBand = age >= bandMin && age <= effectiveMax;
        String relaxNote = applied
                ? " after printed " + category + " +" + (extra == (int) extra ? String.valueOf((int) extra) : extra)
                : (category != null ? " (" + category + ", no printed relaxation)" : "");
        if (inBand) {
            return reason(
                    "age",
                    "pass",
                    "Age " + age + " on " + asOn + " is within " + num(bandMin) + "–" + num(effectiveMax) + relaxNote);
        }
        return reason(
                "age",
                "fail",
                "Age " + age + " on " + asOn + " is outside " + num(bandMin) + "–" + num(effectiveMax) + relaxNote);
    }

    static Map<String, Object> evaluateEducation(Map<String, Object> profile, Map<String, Object> extracted) {
        Object required = extracted.get("minEducation");
        if (required == null) {
            return reason("education", "unknown", "Education requirement not parsed — verify on official site");
        }
        if ("experience".equals(required)) {
            return reason(
                    "education",
                    "unknown",
                    "Experience-based requirement is not compared to a degree — verify on official site");
        }
        String outcome = EligibilityFacts.compareEducation(profile.get("highestEducation"), required);
        Object have = EligibilityFacts.normalizeQualification(profile.get("highestEducation"));
        if (have == null) have = profile.get("highestEducation");
        if ("unknown".equals(outcome)) {
            return reason("education", "unknown", "Education requirement not parsed — verify on official site");
        }
        if ("pass".equals(outcome)) {
            return reason("education", "pass", have + " meets " + required + " requirement");
        }
        return reason("education", "fail", have + " is below " + required + " requirement");
    }

    static Map<String, Object> evaluateDiscipline(Map<String, Object> profile, Map<String, Object> extracted) {
        Object required = extracted.get("disciplineRequired");
        if (required == null || "".equals(required) || "any".equals(required)) {
            return reason("discipline", "pass", "No discipline requirement");
        }
        String known = EligibilityFacts.normalizeDiscipline(required);
        if (known == null) {
            return reason("discipline", "unknown", "Discipline requirement not parsed — verify on official site");
        }
        String have = EligibilityFacts.normalizeDiscipline(profile.get("educationDiscipline"));
        if ("any".equals(have) || known.equals(have)) {
            return reason("discipline", "pass", (have == null ? "any" : have) + " matches " + known);
        }
        if (have == null) {
            return reason("discipline", "fail", "Required " + known + "; profile discipline missing");
        }
        return reason("discipline", "fail", "Required " + known + "; profile has " + have);
    }

    static Map<String, Object> evaluateDomicile(Map<String, Object> profile, Map<String, Object> extracted) {
        if (!Boolean.TRUE.equals(extracted.get("domicileRequired"))) {
            return reason("domicile", "pass", "No domicile restriction");
        }
        List<String> required = EligibilityFacts.asStringArray(extracted.get("domicileStates")).stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .toList();
        if (required.isEmpty()) {
            return reason(
                    "domicile",
                    "unknown",
                    "Local-candidate requirement listed without states — verify on official site");
        }
        List<String> haveList = new ArrayList<>();
        if (profile.get("domicileStates") instanceof List<?> ds) {
            for (Object s : ds) {
                if (s != null) haveList.add(String.valueOf(s).toUpperCase(Locale.ROOT));
            }
        }
        if (profile.get("birthState") != null) {
            haveList.add(String.valueOf(profile.get("birthState")).toUpperCase(Locale.ROOT));
        }
        Set<String> have = haveList.stream().filter(s -> !s.isBlank()).collect(Collectors.toSet());
        List<String> overlap = required.stream().filter(have::contains).toList();
        if (!overlap.isEmpty()) {
            return reason("domicile", "pass", "Domicile overlaps " + String.join(", ", overlap));
        }
        return reason(
                "domicile",
                "fail",
                "Required domicile " + String.join(", ", required) + "; profile has "
                        + (have.isEmpty() ? "none" : String.join(", ", have)));
    }

    static Map<String, Object> evaluateGender(Map<String, Object> profile, Map<String, Object> extracted) {
        String required = EligibilityFacts.normalizeGender(extracted.get("genderRequired"));
        if (required == null) {
            return reason("gender", "pass", "No gender restriction");
        }
        String have = EligibilityFacts.normalizeGender(profile.get("gender"));
        if (required.equals(have)) {
            return reason("gender", "pass", "Matches " + required + "-only requirement");
        }
        if (have == null) {
            return reason("gender", "fail", "This post requires " + required + "; profile gender is missing");
        }
        return reason("gender", "fail", "This post requires " + required + "; profile gender is " + have);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> evaluatePwbd(Map<String, Object> profile, Map<String, Object> extracted) {
        boolean hasDisability = profile.get("pwbd") instanceof Map<?, ?> pw
                && Boolean.TRUE.equals(((Map<String, Object>) pw).get("hasDisability"));
        if (!hasDisability) {
            return reason("pwbd", "pass", "No disability declared");
        }
        String category = EligibilityFacts.normalizePwbdCategory(
                profile.get("pwbd") instanceof Map<?, ?> pw ? ((Map<String, Object>) pw).get("category") : null);
        if (extracted.get("posts") instanceof List<?> posts && !posts.isEmpty()) {
            boolean anyNull = false;
            List<Map<String, Object>> suitable = new ArrayList<>();
            for (Object p : posts) {
                if (!(p instanceof Map<?, ?> pm)) continue;
                Boolean allow = postAllowsPwbd((Map<String, Object>) pm, category);
                if (allow == null) anyNull = true;
                else if (allow) suitable.add((Map<String, Object>) pm);
            }
            if (anyNull) {
                return reason("pwbd", "unknown", "PwBD mentioned — verify official post-wise suitability");
            }
            if (suitable.isEmpty()) {
                return reason("pwbd", "fail", "Listed posts do not include a PwBD vacancy for this category");
            }
            if (suitable.size() == posts.size()) {
                return reason("pwbd", "pass", "PwBD vacancies listed on all " + posts.size() + " posts");
            }
            String names = suitable.stream()
                    .map(s -> String.valueOf(s.get("title")))
                    .limit(6)
                    .collect(Collectors.joining(", "));
            return reason("pwbd", "pass", "PwBD suitable posts: " + names);
        }
        if (Boolean.TRUE.equals(extracted.get("pwbdAllowed"))) {
            if (extracted.get("pwbdCategories") instanceof List<?> cats && !cats.isEmpty()) {
                if (category == null) {
                    return reason("pwbd", "unknown", "PwBD mentioned — verify official post-wise suitability");
                }
                if (cats.contains(category)) {
                    return reason("pwbd", "pass", "PwBD category " + category + " is listed");
                }
                return reason(
                        "pwbd",
                        "fail",
                        "Listed PwBD categories are "
                                + cats.stream().map(String::valueOf).collect(Collectors.joining(", ")));
            }
            return reason("pwbd", "pass", "PwBD vacancies mentioned");
        }
        if (Boolean.FALSE.equals(extracted.get("pwbdAllowed"))) {
            return reason("pwbd", "fail", "Notification does not allow PwBD");
        }
        return reason("pwbd", "unknown", "PwBD suitability not listed — verify on official site");
    }

    static Map<String, Object> evaluateCategory(Map<String, Object> profile, Map<String, Object> extracted) {
        String have = EligibilityFacts.normalizeCategory(profile.get("reservationCategory"));
        List<String> listed = extracted.get("openToCategories") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList()
                : null;
        if (listed == null && !Boolean.TRUE.equals(extracted.get("reservedOnly"))) {
            return reason("category", "pass", "No reserved-only restriction listed");
        }
        if (Boolean.TRUE.equals(extracted.get("reservedOnly")) && listed == null) {
            return reason("category", "unknown", "Reserved-only listed without categories — verify on official site");
        }
        if (listed != null && listed.contains(have)) {
            return reason("category", "pass", have + " is in the listed categories");
        }
        return reason(
                "category",
                "fail",
                "Open to " + (listed == null ? "" : String.join(", ", listed)) + " only; profile is " + have);
    }

    private static Boolean postAllowsPwbd(Map<String, Object> post, String category) {
        if (Boolean.FALSE.equals(post.get("pwbdAllowed"))) return false;
        if (!Boolean.TRUE.equals(post.get("pwbdAllowed"))) return null;
        if (!(post.get("pwbdCategories") instanceof List<?> cats) || cats.isEmpty()) return true;
        if (category == null) return null;
        return cats.contains(category);
    }

    private static String closeSortKey(Map<String, Object> extracted) {
        Object close = extracted.get("applicationClose");
        return close == null || String.valueOf(close).isBlank() ? "9999-12-31" : String.valueOf(close);
    }

    private static List<Map<String, Object>> rankMatches(List<Map<String, Object>> rows) {
        return rows.stream()
                .sorted(Comparator.comparing((Map<String, Object> a) -> String.valueOf(a.get("_closeSortKey")))
                        .thenComparing((a, b) -> Double.compare(
                                ((Number) b.get("score")).doubleValue(), ((Number) a.get("score")).doubleValue())))
                .toList();
    }

    private static Map<String, Object> stripInternal(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.remove("_closeSortKey");
        out.remove("fails");
        return out;
    }

    private static Object first(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String num(double d) {
        return d == (int) d ? String.valueOf((int) d) : String.valueOf(d);
    }
}
