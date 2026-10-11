package in.govtjobs.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchUnknownTest {

    @Test
    void thinNoticeStaysMatchedWithUnknownRulesAndVerifyFlag() {
        Map<String, Object> thin = notice("thin");
        Map<String, Object> row = onlyMatch(baseProfile(), thin);

        assertThat(reason(row, "age").get("outcome")).isEqualTo("unknown");
        assertThat(reason(row, "education").get("outcome")).isEqualTo("unknown");
        assertThat(reason(row, "discipline").get("detail"))
                .isEqualTo("Discipline not listed — verify on official site");
        assertThat(reason(row, "domicile").get("detail"))
                .isEqualTo("Domicile not listed — verify on official site");
        assertThat(reason(row, "gender").get("detail"))
                .isEqualTo("Gender restriction not listed — verify on official site");
        assertThat(reason(row, "category").get("detail"))
                .isEqualTo("Category restriction not listed — verify on official site");
        assertThat(reason(row, "pwbd").get("detail")).isEqualTo("No disability declared");
        assertThat(reason(row, "status").get("outcome")).isEqualTo("pass");
        assertThat(row.get("lowConfidence")).isEqualTo(true);
        assertNoEligible(row);

        Map<String, Object> extracted = EligibilityFacts.extractOpportunityFacts(thin);
        assertThat(extracted.get("domicileRequired")).isNull();
        assertThat(extracted.get("reservedOnly")).isNull();
    }

    @Test
    void explicitUnrestrictedDomicileAndCategoryPass() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("domicileRequired", false);
        facts.put("reservedOnly", false);
        Map<String, Object> extracted = EligibilityFacts.extractOpportunityFacts(facts);
        assertThat(extracted.get("domicileRequired")).isEqualTo(false);
        assertThat(extracted.get("reservedOnly")).isEqualTo(false);

        Map<String, Object> open = notice("open-rules");
        open.put("domicileRequired", false);
        Map<String, Object> row = onlyMatch(baseProfile(), open);
        assertThat(reason(row, "domicile").get("outcome")).isEqualTo("pass");
        assertThat(reason(row, "domicile").get("detail")).isEqualTo("No domicile restriction");

        Map<String, Object> reservedBlank = notice("reserved-blank");
        reservedBlank.put("reservedOnly", true);
        Map<String, Object> unknownCategories = onlyMatch(baseProfile(), reservedBlank);
        assertThat(reason(unknownCategories, "category").get("detail"))
                .isEqualTo("Reserved-only listed without categories — verify on official site");
    }

    @Test
    void birthStateStillSatisfiesARealDomicileRule() {
        Map<String, Object> profile = new LinkedHashMap<>(baseProfile());
        profile.put("birthState", "UP");
        profile.put("domicileStates", List.of("MH"));
        Map<String, Object> post = notice("up-local");
        post.put("domicileRequired", true);
        post.put("domicileStates", List.of("UP"));

        Map<String, Object> row = onlyMatch(profile, post);
        assertThat(reason(row, "domicile").get("outcome")).isEqualTo("pass");
        assertThat(String.valueOf(reason(row, "domicile").get("detail"))).contains("UP");
    }

    @Test
    void profileAnyOrBlankDisciplineDoesNotPassOrFailANamedDiscipline() {
        Map<String, Object> post = notice("eng");
        post.put("disciplineRequired", "engineering");

        Map<String, Object> any = new LinkedHashMap<>(baseProfile());
        any.put("educationDiscipline", "any");
        Map<String, Object> anyRow = onlyMatch(any, post);
        assertThat(reason(anyRow, "discipline").get("outcome")).isEqualTo("unknown");
        assertThat(String.valueOf(reason(anyRow, "discipline").get("detail")))
                .contains("did not name")
                .contains("engineering");

        Map<String, Object> blank = new LinkedHashMap<>(baseProfile());
        blank.put("educationDiscipline", "");
        Map<String, Object> blankRow = onlyMatch(blank, post);
        assertThat(reason(blankRow, "discipline").get("outcome")).isEqualTo("unknown");

        Map<String, Object> engineer = new LinkedHashMap<>(baseProfile());
        engineer.put("educationDiscipline", "engineering");
        assertThat(reason(onlyMatch(engineer, post), "discipline").get("outcome")).isEqualTo("pass");

        Map<String, Object> commerce = new LinkedHashMap<>(baseProfile());
        commerce.put("educationDiscipline", "commerce");
        Map<String, Object> failed = onlyExcluded(commerce, post);
        assertThat(reason(failed, "discipline").get("outcome")).isEqualTo("fail");
    }

    @Test
    void blankGenderAgainstFemaleStaysMatchedAndMaleFails() {
        Map<String, Object> post = notice("women");
        post.put("genderRequired", "female");

        Map<String, Object> blank = new LinkedHashMap<>(baseProfile());
        blank.put("gender", "");
        Map<String, Object> blankRow = onlyMatch(blank, post);
        assertThat(reason(blankRow, "gender").get("outcome")).isEqualTo("unknown");
        assertThat(String.valueOf(reason(blankRow, "gender").get("detail"))).containsIgnoringCase("verify");

        Map<String, Object> female = new LinkedHashMap<>(baseProfile());
        female.put("gender", "female");
        assertThat(reason(onlyMatch(female, post), "gender").get("outcome")).isEqualTo("pass");

        Map<String, Object> failed = onlyExcluded(baseProfile(), post);
        assertThat(reason(failed, "gender").get("outcome")).isEqualTo("fail");
    }

    @Test
    void itiIsNotAboveOrBelowAnotherRung() {
        for (String rung : List.of("below_10", "10th", "12th", "diploma", "graduate", "pg", "phd")) {
            assertThat(EligibilityFacts.compareEducation("iti", rung)).isEqualTo("unknown");
            assertThat(EligibilityFacts.compareEducation(rung, "iti")).isEqualTo("unknown");
        }
        assertThat(EligibilityFacts.compareEducation("iti", "iti")).isEqualTo("pass");
        assertThat(EligibilityFacts.compareEducation("12th", "graduate")).isEqualTo("fail");
        assertThat(EligibilityFacts.compareEducation("graduate", "diploma")).isEqualTo("pass");
        assertThat(EligibilityFacts.compareEducation("graduate", "experience")).isEqualTo("unknown");
        assertThat(EligibilityFacts.compareEducation("experience", "iti")).isEqualTo("unknown");

        Map<String, Object> itiProfile = new LinkedHashMap<>(baseProfile());
        itiProfile.put("highestEducation", "iti");
        Map<String, Object> twelfth = notice("twelfth");
        twelfth.put("minEducation", "12th");
        Map<String, Object> itiRow = onlyMatch(itiProfile, twelfth);
        assertThat(reason(itiRow, "education").get("outcome")).isEqualTo("unknown");
        String detail = String.valueOf(reason(itiRow, "education").get("detail"));
        assertThat(detail).containsIgnoringCase("verify");
        assertThat(detail).doesNotContainIgnoringCase("meets");
        assertNoEligible(itiRow);

        Map<String, Object> twelfthProfile = new LinkedHashMap<>(baseProfile());
        twelfthProfile.put("highestEducation", "12th");
        Map<String, Object> graduateFloor = notice("graduate-floor");
        graduateFloor.put("minEducation", "graduate");
        assertThat(reason(onlyExcluded(twelfthProfile, graduateFloor), "education").get("outcome"))
                .isEqualTo("fail");

        Map<String, Object> diplomaFloor = notice("diploma-floor");
        diplomaFloor.put("minEducation", "diploma");
        assertThat(reason(onlyMatch(baseProfile(), diplomaFloor), "education").get("outcome"))
                .isEqualTo("pass");

        Map<String, Object> itiFloor = notice("iti-floor");
        itiFloor.put("minEducation", "iti");
        assertThat(reason(onlyMatch(itiProfile, itiFloor), "education").get("outcome")).isEqualTo("pass");
    }

    @Test
    void pastLastDateIsExcludedByTheDomainMethod() {
        Map<String, Object> past = notice("past");
        past.put("lastDate", "2020-01-15");
        Map<String, List<Map<String, Object>>> onlyPast =
                EligibilityMatch.matchOpportunities(baseProfile(), List.of(past));
        assertThat(onlyPast.get("matches")).isEmpty();
        assertThat(onlyPast.get("excluded")).extracting(row -> row.get("id")).containsExactly("past");
        assertThat(reason(onlyPast.get("excluded").get(0), "status").get("outcome")).isEqualTo("fail");

        Map<String, Object> future = notice("future");
        Map<String, List<Map<String, Object>>> both =
                EligibilityMatch.matchOpportunities(baseProfile(), List.of(future, past));
        assertThat(both.get("matches")).extracting(row -> row.get("id")).containsExactly("future");
        assertThat(both.get("excluded")).extracting(row -> row.get("id")).containsExactly("past");
    }

    @Test
    void lowConfidenceFollowsAnyUnknownEvenAboveSixtyPercent() {
        Map<String, Object> almost = fullySpecified("almost");
        almost.remove("disciplineRequired");
        almost.remove("genderRequired");
        Map<String, Object> partial = onlyMatch(baseProfile(), almost);
        assertThat(((Number) partial.get("confidence")).doubleValue()).isEqualTo(0.75);
        assertThat(partial.get("lowConfidence")).isEqualTo(true);

        Map<String, Object> complete = onlyMatch(baseProfile(), fullySpecified("complete"));
        assertThat(complete.get("lowConfidence")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reasons = (List<Map<String, Object>>) complete.get("reasons");
        assertThat(reasons).hasSize(8);
        assertThat(reasons).allSatisfy(r -> assertThat(r.get("outcome")).isEqualTo("pass"));
        assertNoEligible(complete);
    }

    private static Map<String, Object> fullySpecified(String id) {
        Map<String, Object> row = notice(id);
        row.put("ageMin", 18);
        row.put("ageMax", 40);
        row.put("ageAsOnDate", "2026-08-01");
        row.put("minEducation", "graduate");
        row.put("disciplineRequired", "any");
        row.put("domicileRequired", false);
        row.put("genderRequired", "male");
        row.put("reservedOnly", false);
        return row;
    }

    private static Map<String, Object> notice(String id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", id);
        row.put("officialUrl", "https://ssc.gov.in/");
        row.put("lastDate", "2027-12-01");
        return row;
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

    private static Map<String, Object> onlyMatch(Map<String, Object> profile, Map<String, Object> opp) {
        Map<String, List<Map<String, Object>>> out =
                EligibilityMatch.matchOpportunities(profile, List.of(opp));
        assertThat(out.get("excluded")).isEmpty();
        assertThat(out.get("matches")).hasSize(1);
        return out.get("matches").get(0);
    }

    private static Map<String, Object> onlyExcluded(Map<String, Object> profile, Map<String, Object> opp) {
        Map<String, List<Map<String, Object>>> out =
                EligibilityMatch.matchOpportunities(profile, List.of(opp));
        assertThat(out.get("matches")).isEmpty();
        assertThat(out.get("excluded")).hasSize(1);
        return out.get("excluded").get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> reason(Map<String, Object> row, String rule) {
        List<Map<String, Object>> reasons = (List<Map<String, Object>>) row.get("reasons");
        return reasons.stream().filter(r -> rule.equals(r.get("rule"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static void assertNoEligible(Map<String, Object> row) {
        List<Map<String, Object>> reasons = (List<Map<String, Object>>) row.get("reasons");
        assertThat(reasons).allSatisfy(r -> assertThat(String.valueOf(r.get("detail")))
                .doesNotContainIgnoringCase("eligible"));
    }
}
