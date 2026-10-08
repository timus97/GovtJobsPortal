package in.govtjobs.web.collect;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogFetchPageTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogCollectService collect;

    @BeforeEach
    void idle() throws Exception {
        when(collect.status()).thenReturn(Map.of(
                "running", false,
                "completed", 0,
                "total", 0,
                "currentSource", "",
                "startedBy", "",
                "lastSummary", "",
                "report", Map.of("sources", List.of(), "startedAt", "")));
        when(collect.sources()).thenReturn(List.of(Map.of(
                "sourceId", "demo",
                "label", "demo — Demo board")));
        when(collect.enabledCount()).thenReturn(1);
    }

    @Test
    void visitorsAreSentToTheOperatorLogin() throws Exception {
        mvc.perform(get("/ops/fetch")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/ops/login"));
    }

    @Test
    void operatorCanOpenFetchAndStartADailyRun() throws Exception {
        when(collect.startDaily("ada")).thenReturn(new CatalogCollectService.Start(true, ""));
        mvc.perform(get("/ops/fetch").with(user("ada").roles("OPS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Run enabled sources")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("demo — Demo board")));
        mvc.perform(post("/ops/fetch/daily").with(csrf()).with(user("ada").roles("OPS")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ops/fetch"));
        verify(collect).startDaily("ada");
    }

    @Test
    void sourcesPageRendersTheRegistry() throws Exception {
        mvc.perform(get("/ops/sources").with(user("ada").roles("OPS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registry sources")));
    }
}
