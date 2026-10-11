package in.govtjobs.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void landingRendersSignIn() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A quieter way to look at open windows")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create account")));
    }

    @Test
    void registerTabRendersCreateCopy() throws Exception {
        mvc.perform(get("/").param("tab", "register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create an account")));
    }

    @Test
    void opsLoginRenders() throws Exception {
        mvc.perform(get("/ops/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Admin console")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
    }

    @Test
    void registerValidationShowsNotice() throws Exception {
        mvc.perform(post("/account/register").with(csrf()).param("email", "not-an-email").param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Password")));
    }

    @Test
    void loginReturnsToASafeJobPath() throws Exception {
        String email = "return-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/account/register").with(csrf()).param("email", email).param("password", "password1234"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/account/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "password1234")
                        .param("next", "/jobs/321c05e538ceea7a"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/jobs/321c05e538ceea7a"));
        mvc.perform(post("/account/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "password1234")
                        .param("next", "https://evil.example/jobs/a"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/match"));
    }

    @Test
    void forgotPasswordPageRenders() throws Exception {
        mvc.perform(get("/account/forgot"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Forgot password")));
    }
}
