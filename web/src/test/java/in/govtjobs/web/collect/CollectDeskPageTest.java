package in.govtjobs.web.collect;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CollectDeskPageTest {

    @Autowired
    private MockMvc mvc;

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review queue")));
        mvc.perform(get("/ops/pipeline/priority").with(user("ada").roles("OPS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Priority links")));
        mvc.perform(post("/ops/pipeline/keywords")
                        .with(csrf())
                        .with(user("ada").roles("OPS"))
                        .param("phrase", "gazette"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ops/pipeline/priority"));
        mvc.perform(post("/ops/pipeline/links")
                        .with(csrf())
                        .with(user("ada").roles("OPS"))
                        .param("url", "http://example.com"))
                .andExpect(status().is3xxRedirection());
    }
}
