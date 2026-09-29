package in.govtjobs.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "govtjobs.feature.student=false")
@AutoConfigureMockMvc
class FeatureStudentOffTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void catalogStaysPublicWhileDeskAndRegisterStayClosed() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isOk());
        mvc.perform(get("/dashboard").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/match").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/profile").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/account/register")
                        .with(csrf())
                        .param("email", "off@example.com")
                        .param("password", "password1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
