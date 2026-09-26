package in.govtjobs.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpportunitySchemaTest {

    @Test
    void isValidOpportunity_requiresHttpsAndSelection() {
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("id", "o1");
        ok.put("title", "JE");
        ok.put("organization", "SSC");
        ok.put("officialUrl", "https://ssc.gov.in/");
        ok.put("hasExam", true);
        ok.put("selectionProcess", "cbt");
        ok.put("status", "open");
        assertThat(OpportunitySchema.isValidOpportunity(ok)).isEmpty();

        Map<String, Object> http = new LinkedHashMap<>(ok);
        http.put("officialUrl", "http://ssc.gov.in/");
        assertThat(OpportunitySchema.isValidOpportunity(http))
                .anyMatch(e -> e.contains("officialUrl"));

        Map<String, Object> multi = new LinkedHashMap<>(ok);
        multi.put("selectionProcesses", List.of("cbt", "not_a_code"));
        assertThat(OpportunitySchema.isValidOpportunity(multi))
                .contains("invalid selectionProcesses");
    }
}
