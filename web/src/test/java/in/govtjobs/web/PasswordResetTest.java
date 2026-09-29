package in.govtjobs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.govtjobs.web.store.PasswordResetService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordResetService resets;

    @Test
    void forgotShowsDevLinkAndResetSignsIn() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/account/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "password1234"))
                .andExpect(status().is3xxRedirection());
        MvcResult forgot = mvc.perform(post("/account/forgot").with(csrf()).param("email", email))
                .andExpect(status().isOk())
                .andReturn();
        String html = forgot.getResponse().getContentAsString();
        assertThat(html).contains("Open the one-time reset link");
        Map<String, Object> issued = resets.requestReset(email);
        String url = String.valueOf(issued.get("devResetUrl"));
        String token = url.substring(url.indexOf("token=") + 6);
        MvcResult reset = mvc.perform(post("/account/reset")
                        .with(csrf())
                        .param("token", token)
                        .param("password", "newpassword1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn();
        assertThat(reset.getResponse().getCookie("student_session")).isNotNull();
        mvc.perform(get("/account/reset").param("token", token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("invalid or has expired")));
    }
}
