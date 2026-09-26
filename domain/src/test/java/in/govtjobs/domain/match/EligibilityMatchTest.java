package in.govtjobs.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class EligibilityMatchTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<Map<String, Object>> golden = List.of();

    private static final Map<String, Object> BASE_PROFILE = baseProfile();

    @BeforeAll
    static void loadGolden() throws Exception {
        Path path = goldenPath();
        if (Files.isRegularFile(path)) {
            golden = MAPPER.readValue(Files.readString(path), new TypeReference<>() {});
        }
    }

    static boolean goldenUsable() {
        return Files.isRegularFile(goldenPath());
    }

    private static Path goldenPath() {
        return RepoPaths.root().resolve("tests/fixtures/golden-opportunities.json");
    }

    private static Map<String, Object> baseProfile() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("dob", "1998-06-15");
        p.put("highestEducation", "graduate");
        p.put("educationDiscipline", "any");
        p.put("birthState", "MH");
        p.put("domicileStates", List.of("MH"));
        p.put("gender", "male");
        p.put("pwbd", Map.of("hasDisability", false, "category", "none"));
        p.put("reservationCategory", "UR");
        return p;
    }

    private static Map<String, Object> byId(String id) {
        return golden.stream()
                .filter(o -> id.equals(o.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing golden fixture " + id));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> matches(Map<String, List<Map<String, Object>>> bundle) {
        return bundle.get("matches");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> excluded(Map<String, List<Map<String, Object>>> bundle) {
        return bundle.get("excluded");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> reason(Map<String, Object> row, String rule) {
        List<Map<String, Object>> reasons = (List<Map<String, Object>>) row.get("reasons");
        return reasons.stream()
                .filter(r -> rule.equals(r.get("rule")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void educationLadderAndAgeOnNotificationDate() {
        assertThat(EligibilityFacts.normalizeQualification("postgraduate")).isEqualTo("pg");
        assertThat(EligibilityFacts.educationRank("pg"))
                .isEqualTo(EligibilityFacts.educationRank("postgraduate"));
        assertThat(EligibilityFacts.compareEducation("graduate", "12th")).isEqualTo("pass");
        assertThat(EligibilityFacts.compareEducation("12th", "graduate")).isEqualTo("fail");
        assertThat(EligibilityFacts.compareEducation("graduate", "experience")).isEqualTo("unknown");
        assertThat(EligibilityFacts.compareEducation("experience", "graduate")).isEqualTo("unknown");

        assertThat(EligibilityFacts.ageOnDate("1995-08-02", "2026-08-01")).isEqualTo(30);
        assertThat(EligibilityFacts.ageOnDate("1995-08-02", "2026-08-19")).isEqualTo(31);
    }

    @Test
    void scoreIs100TimesConfidenceWhenNoFailsElseZero() {
        Map<String, Object> opp = new LinkedHashMap<>();
        opp.put("id", "sparse");
        opp.put("officialUrl", "https://ncs.gov.in/");
        opp.put("lastDate", null);
        opp.put("qualification", null);

        Map<String, Object> sparseProfile = new LinkedHashMap<>(BASE_PROFILE);
        sparseProfile.put("pwbd", Map.of("hasDisability", true, "category", "OH"));
        Map<String, List<Map<String, Object>>> sparse =
                EligibilityMatch.matchOpportunities(sparseProfile, List.of(opp));
        Map<String, Object> row = matches(sparse).get(0);
        double confidence = ((Number) row.get("confidence")).doubleValue();
        double score = ((Number) row.get("score")).doubleValue();
        assertThat(confidence).isLessThan(EligibilityMatch.LOW_CONFIDENCE);
        assertThat(score).isEqualTo(Math.round(100 * confidence * 100d) / 100d);
        assertThat(row.get("lowConfidence")).isEqualTo(true);

        Map<String, Object> failOpp = new LinkedHashMap<>();
        failOpp.put("id", "edu-fail");
        failOpp.put("officialUrl", "https://ssc.gov.in/");
        failOpp.put("lastDate", "2027-12-01");
        failOpp.put("minEducation", "graduate");
        Map<String, Object> lowEdu = new LinkedHashMap<>(BASE_PROFILE);
        lowEdu.put("highestEducation", "12th");
        Map<String, List<Map<String, Object>>> failed =
                EligibilityMatch.matchOpportunities(lowEdu, List.of(failOpp));
        assertThat(excluded(failed)).hasSize(1);
        assertThat(((Number) excluded(failed).get(0).get("score")).doubleValue()).isZero();
    }

    @Test
    void unknownDoesNotFail_andMissingProfileThrows400() {
        Map<String, Object> textOnly = new LinkedHashMap<>();
        textOnly.put("id", "text-only");
        textOnly.put("title", "Unparsed notice");
        textOnly.put("officialUrl", "https://upsc.gov.in/");
        textOnly.put("lastDate", "2027-12-01");
        textOnly.put("eligibility", List.of("Age 21-30 years as on 01.08.2026", "Must be graduate"));

        Map<String, Object> extracted = EligibilityFacts.extractOpportunityFacts(textOnly);
        assertThat(extracted.get("ageMin")).isNull();
        assertThat(extracted.get("ageMax")).isNull();
        assertThat(extracted.get("minEducation")).isNull();

        Map<String, List<Map<String, Object>>> textMatch =
                EligibilityMatch.matchOpportunities(BASE_PROFILE, List.of(textOnly));
        assertThat(reason(matches(textMatch).get(0), "age").get("outcome")).isEqualTo("unknown");
        assertThat(reason(matches(textMatch).get(0), "education").get("outcome"))
                .isEqualTo("unknown");

        Map<String, Object> missingCat = new LinkedHashMap<>(BASE_PROFILE);
        missingCat.put("reservationCategory", null);
        assertThatThrownBy(() -> EligibilityMatch.matchOpportunities(missingCat, List.of()))
                .isInstanceOf(MatchException.class)
                .satisfies(ex -> assertThat(((MatchException) ex).getStatusCode()).isEqualTo(400));

        EligibilityFacts.ValidationResult v =
                EligibilityFacts.validateMatchProfile(Map.of("dob", "1998-01-01"));
        assertThat(v.ok()).isFalse();
    }

    @Test
    @EnabledIf("goldenUsable")
    void goldenOpportunities_coreMatchSemantics() {
        assertThat(golden.size()).isGreaterThanOrEqualTo(6);

        Map<String, Object> asOnProfile = new LinkedHashMap<>(BASE_PROFILE);
        asOnProfile.put("dob", "1995-08-02");
        Map<String, List<Map<String, Object>>> ageBand =
                EligibilityMatch.matchOpportunities(asOnProfile, List.of(byId("golden-age-band")));
        Map<String, Object> ageMatch = matches(ageBand).stream()
                .filter(m -> "golden-age-band".equals(m.get("id")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> ageReason = reason(ageMatch, "age");
        assertThat(ageReason.get("outcome")).isEqualTo("pass");
        assertThat(String.valueOf(ageReason.get("detail"))).contains("Age 30 on 2026-08-01");
        assertThat(String.valueOf(ageReason.get("detail"))).doesNotContainIgnoringCase("today");

        Map<String, Object> obcProfile = new LinkedHashMap<>(BASE_PROFILE);
        obcProfile.put("dob", "1994-01-01");
        obcProfile.put("reservationCategory", "OBC");
        Map<String, List<Map<String, Object>>> obcPrinted =
                EligibilityMatch.matchOpportunities(obcProfile, List.of(byId("golden-age-band")));
        Map<String, Object> obcRow = matches(obcPrinted).stream()
                .filter(m -> "golden-age-band".equals(m.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(reason(obcRow, "age").get("detail"))).contains("printed OBC +3");

        Map<String, List<Map<String, Object>>> invented = EligibilityMatch.matchOpportunities(
                obcProfile, List.of(byId("golden-no-invented-relaxation")));
        assertThat(excluded(invented).stream()
                        .anyMatch(m -> "golden-no-invented-relaxation".equals(m.get("id"))))
                .isTrue();
        Map<String, Object> inventedAge = reason(
                excluded(invented).stream()
                        .filter(m -> "golden-no-invented-relaxation".equals(m.get("id")))
                        .findFirst()
                        .orElseThrow(),
                "age");
        assertThat(inventedAge.get("outcome")).isEqualTo("fail");
        assertThat(String.valueOf(inventedAge.get("detail"))).doesNotContain("+3");

        Map<String, List<Map<String, Object>>> missingDate = EligibilityMatch.matchOpportunities(
                BASE_PROFILE, List.of(byId("golden-missing-lastdate")));
        Map<String, Object> missingRow = matches(missingDate).stream()
                .filter(m -> "golden-missing-lastdate".equals(m.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(reason(missingRow, "status").get("outcome")).isEqualTo("unknown");
        assertThat(reason(missingRow, "status").get("detail"))
                .isEqualTo(EligibilityMatch.LAST_DATE_UNKNOWN_CHIP);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> missingReasons =
                (List<Map<String, Object>>) missingRow.get("reasons");
        assertThat(missingReasons).hasSize(8);
        long covered = missingReasons.stream()
                .filter(r -> !"unknown".equals(r.get("outcome")))
                .count();
        assertThat(((Number) missingRow.get("confidence")).doubleValue())
                .isEqualTo(Math.round((covered / 8.0) * 10000d) / 10000d);
        assertThat(((Number) missingRow.get("score")).doubleValue())
                .isEqualTo(
                        Math.round(100 * ((Number) missingRow.get("confidence")).doubleValue() * 100d)
                                / 100d);

        Map<String, Object> pwbdProfile = new LinkedHashMap<>(BASE_PROFILE);
        pwbdProfile.put("highestEducation", "12th");
        pwbdProfile.put("pwbd", Map.of("hasDisability", true, "category", "OH"));
        Map<String, List<Map<String, Object>>> pwbdRes = EligibilityMatch.matchOpportunities(
                pwbdProfile, List.of(byId("golden-pwbd-unknown")));
        assertThat(matches(pwbdRes).stream().anyMatch(m -> "golden-pwbd-unknown".equals(m.get("id"))))
                .isTrue();
        assertThat(reason(
                        matches(pwbdRes).stream()
                                .filter(m -> "golden-pwbd-unknown".equals(m.get("id")))
                                .findFirst()
                                .orElseThrow(),
                        "pwbd")
                .get("outcome"))
                .isEqualTo("unknown");

        Map<String, Object> eduProfile = new LinkedHashMap<>(BASE_PROFILE);
        eduProfile.put("highestEducation", "12th");
        Map<String, List<Map<String, Object>>> eduFail = EligibilityMatch.matchOpportunities(
                eduProfile, List.of(byId("golden-education-fail")));
        Map<String, Object> eduRow = excluded(eduFail).stream()
                .filter(m -> "golden-education-fail".equals(m.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(reason(eduRow, "education").get("outcome")).isEqualTo("fail");
        assertThat(((Number) eduRow.get("score")).doubleValue()).isZero();

        Map<String, List<Map<String, Object>>> closed =
                EligibilityMatch.matchOpportunities(BASE_PROFILE, List.of(byId("golden-closed")));
        assertThat(excluded(closed).stream().anyMatch(m -> "golden-closed".equals(m.get("id"))))
                .isTrue();
        assertThat(matches(closed).stream().noneMatch(m -> "golden-closed".equals(m.get("id"))))
                .isTrue();

        Map<String, List<Map<String, Object>>> ranked = EligibilityMatch.matchOpportunities(
                BASE_PROFILE,
                List.of(
                        byId("golden-missing-lastdate"),
                        byId("golden-age-band"),
                        byId("golden-pwbd-unknown")));
        List<String> ids =
                matches(ranked).stream().map(m -> String.valueOf(m.get("id"))).toList();
        int je = ids.indexOf("golden-age-band");
        int clerk = ids.indexOf("golden-pwbd-unknown");
        int becil = ids.indexOf("golden-missing-lastdate");
        assertThat(je).isGreaterThanOrEqualTo(0);
        assertThat(je).isLessThan(clerk);
        assertThat(clerk).isLessThan(becil);
    }

    @Test
    @EnabledIf("goldenUsable")
    void goldenHardening_pwbdPostwiseAndReservedOnly() {
        if (golden.stream().noneMatch(g -> "golden-pwbd-postwise".equals(g.get("id")))) {
            return;
        }

        Map<String, Object> ohProfile = new LinkedHashMap<>(BASE_PROFILE);
        ohProfile.put("pwbd", Map.of("hasDisability", true, "category", "OH"));
        Map<String, Object> vhProfile = new LinkedHashMap<>(BASE_PROFILE);
        vhProfile.put("pwbd", Map.of("hasDisability", true, "category", "VH"));

        Map<String, List<Map<String, Object>>> postwise = EligibilityMatch.matchOpportunities(
                ohProfile, List.of(byId("golden-pwbd-postwise")));
        Map<String, Object> postwiseRow = matches(postwise).stream()
                .filter(m -> "golden-pwbd-postwise".equals(m.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(reason(postwiseRow, "pwbd").get("outcome")).isEqualTo("pass");
        assertThat(String.valueOf(reason(postwiseRow, "pwbd").get("detail")))
                .contains("Junior Engineer");

        Map<String, List<Map<String, Object>>> vhNone = EligibilityMatch.matchOpportunities(
                vhProfile, List.of(byId("golden-pwbd-postwise-none")));
        assertThat(excluded(vhNone).stream()
                        .anyMatch(m -> "golden-pwbd-postwise-none".equals(m.get("id"))))
                .isTrue();

        Map<String, List<Map<String, Object>>> ambiguous = EligibilityMatch.matchOpportunities(
                ohProfile, List.of(byId("golden-pwbd-ambiguous-posts")));
        assertThat(reason(
                        matches(ambiguous).stream()
                                .filter(m -> "golden-pwbd-ambiguous-posts".equals(m.get("id")))
                                .findFirst()
                                .orElseThrow(),
                        "pwbd")
                .get("outcome"))
                .isEqualTo("unknown");

        Map<String, List<Map<String, Object>>> urReserved = EligibilityMatch.matchOpportunities(
                BASE_PROFILE, List.of(byId("golden-reserved-scst")));
        assertThat(excluded(urReserved).stream()
                        .anyMatch(m -> "golden-reserved-scst".equals(m.get("id"))))
                .isTrue();

        Map<String, Object> scProfile = new LinkedHashMap<>(BASE_PROFILE);
        scProfile.put("reservationCategory", "SC");
        Map<String, List<Map<String, Object>>> scReserved = EligibilityMatch.matchOpportunities(
                scProfile, List.of(byId("golden-reserved-scst")));
        assertThat(matches(scReserved).stream()
                        .anyMatch(m -> "golden-reserved-scst".equals(m.get("id"))))
                .isTrue();

        Map<String, List<Map<String, Object>>> unstructured = EligibilityMatch.matchOpportunities(
                BASE_PROFILE, List.of(byId("golden-reserved-unstructured")));
        assertThat(reason(
                        matches(unstructured).stream()
                                .filter(m -> "golden-reserved-unstructured".equals(m.get("id")))
                                .findFirst()
                                .orElseThrow(),
                        "category")
                .get("outcome"))
                .isEqualTo("pass");
    }

    @Test
    void matchExamSeries_netPrepareForOnly() {
        Map<String, Object> profile = new LinkedHashMap<>(BASE_PROFILE);
        profile.put("highestEducation", "pg");

        Map<String, Object> net = new LinkedHashMap<>();
        net.put("id", "ugc-net");
        net.put("name", "UGC NET");
        net.put("board", "NTA");
        net.put("officialUrl", "https://ugcnet.nta.nic.in/");
        net.put("minEducation", "pg");
        net.put("applyNever", true);
        net.put("linkedOpportunityIds", List.of());

        Map<String, Object> cgl = new LinkedHashMap<>();
        cgl.put("id", "ssc-cgl");
        cgl.put("name", "Combined Graduate Level");
        cgl.put("board", "SSC");
        cgl.put("officialUrl", "https://ssc.gov.in/");
        cgl.put("minEducation", "graduate");
        cgl.put("applyNever", false);
        cgl.put("linkedOpportunityIds", List.of("opp-1"));

        Map<String, List<Map<String, Object>>> out =
                EligibilityMatch.matchExamSeries(profile, List.of(net, cgl));
        Map<String, Object> netMatch = matches(out).stream()
                .filter(m -> "ugc-net".equals(m.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(netMatch.get("canApply")).isEqualTo(false);
        assertThat(netMatch.get("applyNever")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reasons = (List<Map<String, Object>>) netMatch.get("reasons");
        assertThat(reasons.stream()
                        .anyMatch(r -> EligibilityMatch.AGE_WHEN_NOTIFIED.equals(r.get("detail"))))
                .isTrue();

        Map<String, Object> cglMatch = matches(out).stream()
                .filter(m -> "ssc-cgl".equals(m.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(cglMatch.get("canApply")).isEqualTo(true);

        Map<String, Object> tenth = new LinkedHashMap<>(profile);
        tenth.put("highestEducation", "10th");
        Map<String, List<Map<String, Object>>> low =
                EligibilityMatch.matchExamSeries(tenth, List.of(net, cgl));
        assertThat(excluded(low).stream().anyMatch(m -> "ugc-net".equals(m.get("id"))))
                .isTrue();
    }

    @Test
    void tenKMatchSmokeIsFastEnough() {
        List<Map<String, Object>> synth = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "synth-" + i);
            row.put("title", "Synthetic " + i);
            row.put("organization", "Board");
            row.put("officialUrl", "https://ssc.gov.in/");
            row.put("lastDate", i % 3 == 0 ? null : "2027-12-31");
            row.put("qualification", i % 2 == 0 ? "graduate" : null);
            row.put("ageMin", 18);
            row.put("ageMax", 32);
            row.put("ageAsOnDate", "2026-08-01");
            synth.add(row);
        }
        // Warm-up (Node pr05 targets p95 < 200ms; JVM needs a steady-state sample).
        EligibilityMatch.matchOpportunities(BASE_PROFILE, synth);
        long t0 = System.nanoTime();
        Map<String, List<Map<String, Object>>> out =
                EligibilityMatch.matchOpportunities(BASE_PROFILE, synth);
        double ms = (System.nanoTime() - t0) / 1_000_000.0;
        assertThat(matches(out).size() + excluded(out).size()).isEqualTo(10_000);
        assertThat(ms).as("10k match took %.1fms after warm-up", ms).isLessThan(2000);
    }
}
