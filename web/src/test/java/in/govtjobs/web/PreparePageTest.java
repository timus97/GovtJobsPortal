package in.govtjobs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.govtjobs.web.store.JobStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PreparePageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobStore jobs;

    @Test
    void calendarsFollowTheCatalogAndDoNotInventAnApplyButton() throws Exception {
        List<Map<String, Object>> series = jobs.listExamSeries(null, null);
        assertThat(series).isNotEmpty();
        String html = page(null);

        assertThat(html).contains("Sign in to track", ">Show calendars</button>");
        assertThat(html).doesNotContain("onchange");
        if (series.stream().anyMatch(row -> !Boolean.TRUE.equals(row.get("datesOnFile")))) {
            assertThat(html).contains("No notification, apply, or exam date is on file.");
        }
        if (series.stream().noneMatch(row -> !text(row.get("expectedNotify")).isBlank())) {
            assertThat(html).doesNotContain("Expected notification");
        }
        if (series.stream().noneMatch(row -> !text(row.get("expectedApply")).isBlank())) {
            assertThat(html).doesNotContain("Expected apply window");
        }
        series.stream()
                .filter(row -> "Expected exam (not a single day)".equals(row.get("expectedExamLabel")))
                .map(row -> text(row.get("expectedExamDisplay")))
                .filter(value -> !value.isBlank())
                .distinct()
                .forEach(value -> assertThat(html).contains(value, "Expected exam (not a single day)"));
        series.stream()
                .map(row -> text(row.get("educationLabel")))
                .filter(value -> !value.isBlank())
                .distinct()
                .forEach(label -> assertThat(html).contains(">" + label + "<"));
        if (series.stream().anyMatch(row -> "graduate".equals(row.get("minEducation")))) {
            assertThat(html).doesNotContain(">graduate<");
        }
        if (series.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("applyNever")))) {
            assertThat(html).contains("Not a vacancy");
        }
        if (series.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("canApply")))) {
            assertThat(html).contains("Apply (open window)");
        } else {
            assertThat(html).doesNotContain("Apply (open window)");
        }
    }

    @Test
    void unknownBoardSaysNoCalendarsMatch() throws Exception {
        List<Map<String, Object>> all = jobs.listExamSeries(null, null);
        String name = text(all.get(0).get("name"));
        String unknown = page("not-a-board");
        assertThat(unknown).contains("No calendars match", ">Show calendars</button>", "value=\"not-a-board\"");
        assertThat(unknown).doesNotContain(name);

        List<Map<String, Object>> ssc = jobs.listExamSeries("SSC", null);
        assertThat(ssc).isNotEmpty();
        String board = page("SSC");
        assertThat(board).contains(text(ssc.get(0).get("name")));
        assertThat(board).doesNotContain("No calendars match");
        ssc.stream()
                .filter(row -> "Expected exam (not a single day)".equals(row.get("expectedExamLabel")))
                .map(row -> text(row.get("expectedExamDisplay")))
                .filter(value -> !value.isBlank())
                .findFirst()
                .ifPresent(value -> assertThat(board).contains(value));
    }

    private String page(String board) throws Exception {
        var request = get("/prepare");
        if (board != null) {
            request = request.param("board", board);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
