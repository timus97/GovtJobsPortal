package in.govtjobs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class PublicNavTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void featureOnWithoutASessionShowsPublicNavOnSearchAndPrepare() throws Exception {
        assertSignedOutCatalog(mvc.perform(get("/jobs")));
        assertSignedOutCatalog(mvc.perform(get("/prepare")));
    }

    @Test
    void signedInStudentKeepsDeskLinksAndSignOut() throws Exception {
        assertSignedInCatalog(mvc.perform(get("/jobs").with(user("student@example.com").roles("STUDENT"))));
        assertSignedInCatalog(mvc.perform(get("/prepare").with(user("student@example.com").roles("STUDENT"))));
    }

    @Test
    void publicHeaderLinksJobsPrepareSignInAndAdmin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/jobs\"")))
                .andExpect(content().string(containsString("href=\"/prepare\"")))
                .andExpect(content().string(containsString("href=\"/account/login\"")))
                .andExpect(content().string(containsString("href=\"/ops/login\"")))
                .andExpect(content().string(containsString("nav-link-ops")));
    }

    @Test
    void hidingStudentLinksDoesNotOpenStudentRoutes() throws Exception {
        mvc.perform(get("/match")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        mvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    private static void assertSignedOutCatalog(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/jobs\"")))
                .andExpect(content().string(containsString("href=\"/prepare\"")))
                .andExpect(content().string(containsString("href=\"/account/login\"")))
                .andExpect(content().string(containsString("Sign in")))
                .andExpect(content().string(not(containsString("href=\"/match\""))))
                .andExpect(content().string(not(containsString("href=\"/profile\""))))
                .andExpect(content().string(not(containsString("href=\"/dashboard\""))))
                .andExpect(content().string(not(containsString("action=\"/account/logout\""))))
                .andExpect(content().string(not(containsString("Sign out"))));
    }

    private static void assertSignedInCatalog(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/jobs\"")))
                .andExpect(content().string(containsString("href=\"/prepare\"")))
                .andExpect(content().string(containsString("href=\"/match\"")))
                .andExpect(content().string(containsString("href=\"/profile\"")))
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("action=\"/account/logout\"")))
                .andExpect(content().string(containsString("Sign out")))
                .andExpect(content().string(containsString("student@example.com")))
                .andExpect(content().string(not(containsString("href=\"/account/login\""))));
    }
}
