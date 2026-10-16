package in.govtjobs.web.collect;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CollectDeskPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CrawlDeskStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deskRequiresAnOperator() throws Exception {
        mvc.perform(get("/ops/pipeline")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/ops/login"));
    }

    @Test
    void operatorCanOpenTheDeskAndAddAKeyword() throws Exception {
        mvc.perform(get("/ops/pipeline").with(user("ada").roles("OPS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Waiting for review")));
        mvc.perform(get("/ops/pipeline/review").with(user("ada").roles("OPS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Crawl review")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("pipeline:daily"))));
        mvc.perform(get("/ops/pipeline/priority").with(user("ada").roles("OPS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Priority links")));
        mvc.perform(post("/ops/pipeline/keywords")
                        .with(csrf())
                        .with(user("ada").roles("OPS"))
                        .param("phrase", "gazette"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ops/pipeline/priority"));
        mvc.perform(get("/ops").with(user("ada").roles("OPS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Java collector")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Students see a source run only after Rebuild catalog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Crawl review")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paste review")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("pipeline:daily"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Node pipeline"))));
        mvc.perform(post("/ops/pipeline/links")
                        .with(csrf())
                        .with(user("ada").roles("OPS"))
                        .param("url", "http://example.com"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void crawlNoticeWithoutHttpsIsTextAndApproveDoesNotPublish() throws Exception {
        String mark = "slice6-" + UUID.randomUUID();
        String runId = store.openRun("ada", 1);
        String sourceId = store.openSource(runId, "SSC", "https://ssc.gov.in/");
        store.saveNotice(
                runId,
                sourceId,
                mark + " http",
                "Staff Selection Commission",
                "http://ssc.gov.in/notice",
                null,
                "cbt",
                false,
                "excerpt",
                "kept",
                "");
        store.saveNotice(
                runId,
                sourceId,
                mark + " https",
                "Staff Selection Commission",
                "https://ssc.gov.in/notice",
                null,
                "cbt",
                true,
                "excerpt",
                "kept",
                "");
        try {
            String httpId = jdbc.queryForObject(
                    "SELECT id FROM collect.notices WHERE title = ?", String.class, mark + " http");
            String httpsId = jdbc.queryForObject(
                    "SELECT id FROM collect.notices WHERE title = ?", String.class, mark + " https");
            mvc.perform(get("/ops/pipeline/notices/" + httpId).with(user("ada").roles("OPS")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("http://ssc.gov.in/notice")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("href=\"http://ssc.gov.in/notice\""))))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "It does not add a vacancy to Search, Match, or Prepare.")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(">Approve<")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(">Publish<"))));
            mvc.perform(get("/ops/pipeline/notices/" + httpsId).with(user("ada").roles("OPS")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "href=\"https://ssc.gov.in/notice\"")));
        } finally {
            jdbc.update("DELETE FROM collect.notices WHERE title LIKE ?", mark + "%");
            jdbc.update("DELETE FROM collect.source_results WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM collect.runs WHERE id = ?", runId);
        }
    }
}
