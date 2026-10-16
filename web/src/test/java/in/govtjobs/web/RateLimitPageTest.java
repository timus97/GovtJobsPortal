package in.govtjobs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "govtjobs.rate-limit.login-max=5",
        "govtjobs.rate-limit.window-minutes=15"
})
@AutoConfigureMockMvc
class RateLimitPageTest {

    private static final String BLOCKED = "198.51.100.17";
    private static final String OTHER = "198.51.100." + (20 + Math.floorMod(UUID.randomUUID().hashCode(), 200));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void deleteBuckets() {
        jdbc.update("DELETE FROM rate_limits WHERE bucket_key LIKE ?", "%|" + BLOCKED);
        jdbc.update("DELETE FROM rate_limits WHERE bucket_key LIKE ?", "%|" + OTHER);
    }

    @Test
    void sixthLoginAttemptIsAnAuthPageThatSaysToWait() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/account/login")
                            .with(csrf())
                            .with(from(BLOCKED))
                            .param("email", "nobody@example.com")
                            .param("password", "password1234"))
                    .andExpect(status().isOk());
        }
        MvcResult blocked = mvc.perform(post("/account/login")
                        .with(csrf())
                        .with(from(BLOCKED))
                        .header("X-Forwarded-For", OTHER)
                        .param("email", "nobody@example.com")
                        .param("password", "password1234"))
                .andExpect(status().isTooManyRequests())
                .andReturn();
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getResponse().getForwardedUrl()).isEqualTo("/error/rate-limit");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/error/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("Too many attempts")))
                .andExpect(content().string(containsString("Wait")))
                .andExpect(content().string(containsString("Sarkari Desk")))
                .andExpect(content().string(containsString("Sign in")));

        mvc.perform(post("/account/login")
                        .with(csrf())
                        .with(from(OTHER))
                        .header("X-Forwarded-For", BLOCKED)
                        .param("email", "nobody@example.com")
                        .param("password", "password1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email or password is not correct.")));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
