package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StagingRecordsTest {

    private final SourceSpec source = new SourceSpec(
            "ssc_test",
            "Staff Selection Commission Careers",
            "generic",
            "html_scrape",
            "",
            "central",
            List.of("https://ssc.gov.in/"),
            true);

    @Test
    void missingLocationAndProcessStepsStayBlank() {
        Map<String, Object> item = notice("Junior assistant", "Apply online");
        Map<String, Object> record = StagingRecords.toStagingRecord(item, source, "2026-10-16T00:00:00Z", "html-scrape-v1");
        assertThat(record.get("location")).isEqualTo("");
        assertThat(record.get("processSteps")).isEqualTo(List.of());
        assertThat(String.valueOf(record.get("processSteps"))).doesNotContain("no written exam");
        assertThat(record.get("hasExam")).isNull();
    }

    @Test
    void statedLocationAndStepsAreKept() {
        Map<String, Object> item = notice("Depot associate", "walk-in interview at the depot");
        item.put("location", "All India");
        item.put("processSteps", List.of("Carry the official notice"));
        Map<String, Object> record = StagingRecords.toStagingRecord(item, source, "2026-10-16T00:00:00Z", "html-scrape-v1");
        assertThat(record.get("location")).isEqualTo("All India");
        assertThat(record.get("processSteps")).isEqualTo(List.of("Carry the official notice"));
        assertThat(record.get("hasExam")).isEqualTo(false);
        assertThat(String.valueOf(record.get("processSteps"))).doesNotContain("no written exam");
    }

    @Test
    void emptyParenthesesAndACareersSuffixLeaveTheOrganisationName() {
        SourceSpec careers = new SourceSpec(
                "cpcl",
                "Chennai Petroleum Corporation Limited () Careers",
                "generic",
                "html_scrape",
                "",
                "central",
                List.of("https://cpcl.co.in/"),
                true);
        Map<String, Object> fromSource = StagingRecords.toStagingRecord(
                notice("Refinery assistant", "Apply online"), careers, "2026-10-16T00:00:00Z", "html-scrape-v1");
        assertThat(fromSource.get("organization")).isEqualTo("Chennai Petroleum Corporation Limited");
        assertThat(fromSource.get("sourceName")).isEqualTo("Chennai Petroleum Corporation Limited Careers");

        Map<String, Object> stated = notice("Shipyard associate", "Apply online");
        stated.put("organization", "Cochin Shipyard ()");
        assertThat(StagingRecords.toStagingRecord(stated, source, "2026-10-16T00:00:00Z", "html-scrape-v1").get("organization"))
                .isEqualTo("Cochin Shipyard");
        assertThat(StagingRecords.organisationName("Steel Authority of India Limited ()"))
                .isEqualTo("Steel Authority of India Limited");
        assertThat(StagingRecords.organisationName("GAIL (India) Limited (GAIL) Careers"))
                .isEqualTo("GAIL (India) Limited (GAIL)");
    }

    private static Map<String, Object> notice(String title, String summary) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("href", "https://ssc.gov.in/advt/" + title.hashCode());
        item.put("summary", summary);
        return item;
    }
}
