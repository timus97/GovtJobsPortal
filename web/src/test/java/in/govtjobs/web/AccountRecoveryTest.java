package in.govtjobs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.govtjobs.domain.RepoPaths;
import in.govtjobs.web.store.OperatorStore;
import in.govtjobs.web.store.PasswordResetService;
import in.govtjobs.web.store.StudentStore;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AccountRecoveryTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StudentStore students;

    @Autowired
    private PasswordResetService resets;

    @Autowired
    private OperatorStore operators;

    @Test
    void shortResetPasswordKeepsTheLinkAndALaterPasswordSignsIn() throws Exception {
        String email = "reset7-" + UUID.randomUUID() + "@example.com";
        register(email, "password1234");
        String studentId = String.valueOf(students.findInternalByEmail(email).get("id"));
        String token = tokenFor(email);

        mvc.perform(post("/account/reset").with(csrf()).param("token", token).param("password", "123456789"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Password must be at least 10 characters")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(not(containsString("Link expired"))));
        assertThat(usedAt(studentId)).isNull();
        assertThat(resets.peek(token)).isTrue();

        MvcResult saved = mvc.perform(post("/account/reset")
                        .with(csrf())
                        .param("token", token)
                        .param("password", "1234567890"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn();
        assertThat(saved.getResponse().getCookie("student_session")).isNotNull();
        assertThat(usedAt(studentId)).isNotNull();
        mvc.perform(get("/profile").cookie(saved.getResponse().getCookie("student_session")))
                .andExpect(status().isOk());

        mvc.perform(get("/account/reset").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Link expired")));
        mvc.perform(post("/account/reset").with(csrf()).param("token", "not-a-real-token").param("password", "1234567890"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Link expired")));
    }

    @Test
    void duplicateRegistrationDoesNotConfirmTheEmail() throws Exception {
        String email = "dup7-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/account/register").with(csrf()).param("email", "not-an-email").param("password", "password1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The email is not valid.")))
                .andExpect(content().string(not(containsString("could not be created"))));
        register(email, "password1234");
        mvc.perform(post("/account/register").with(csrf()).param("email", email).param("password", "password1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "The account could not be created. Check the email, or sign in if you already have one.")))
                .andExpect(content().string(not(containsString("not valid"))))
                .andExpect(content().string(not(containsString("registered"))));
    }

    @Test
    void logoutRejectsTheCapturedCookieAndANewLoginWorks() throws Exception {
        String email = "out7-" + UUID.randomUUID() + "@example.com";
        Cookie first = register(email, "password1234");
        String studentId = String.valueOf(students.findInternalByEmail(email).get("id"));
        long before = students.sessionEpoch(studentId);
        mvc.perform(post("/account/logout").with(csrf()).cookie(first))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        assertThat(students.sessionEpoch(studentId)).isEqualTo(before + 1);
        mvc.perform(get("/profile").cookie(first)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));

        MvcResult again = mvc.perform(post("/account/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "password1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/match"))
                .andReturn();
        Cookie second = again.getResponse().getCookie("student_session");
        assertThat(second).isNotNull();
        mvc.perform(get("/profile").cookie(second)).andExpect(status().isOk());
        mvc.perform(get("/profile").cookie(first)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

    @Test
    void operatorPasswordChangeRejectsTheOldCookieAndTheNewLoginWorks() throws Exception {
        Path file = RepoPaths.data().resolve("ops").resolve("operators.json");
        byte[] before = Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        String username = "s7" + UUID.randomUUID().toString().substring(0, 8);
        try {
            operators.create(username, "password1234");
            MvcResult login = mvc.perform(post("/ops/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "password1234"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops"))
                    .andReturn();
            Cookie oldCookie = login.getResponse().getCookie("ops_session");
            assertThat(oldCookie).isNotNull();
            mvc.perform(get("/ops").cookie(oldCookie)).andExpect(status().isOk());

            operators.setPassword(username, "password5678");
            mvc.perform(get("/ops").cookie(oldCookie))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops/login"));

            MvcResult fresh = mvc.perform(post("/ops/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "password5678"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops"))
                    .andReturn();
            Cookie current = fresh.getResponse().getCookie("ops_session");
            assertThat(current).isNotNull();
            mvc.perform(get("/ops").cookie(current)).andExpect(status().isOk());
            mvc.perform(get("/ops").cookie(current)).andExpect(status().isOk());
            mvc.perform(get("/ops").cookie(oldCookie))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops/login"));
        } finally {
            if (before == null) {
                Files.deleteIfExists(file);
            } else {
                Files.write(file, before);
            }
        }
    }

    @Test
    void operatorLogoutRejectsTheCapturedCookieAndANewLoginWorks() throws Exception {
        Path file = RepoPaths.data().resolve("ops").resolve("operators.json");
        byte[] before = Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        String username = "s9" + UUID.randomUUID().toString().substring(0, 8);
        try {
            operators.create(username, "password1234");
            MvcResult login = mvc.perform(post("/ops/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "password1234"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops"))
                    .andReturn();
            Cookie oldCookie = login.getResponse().getCookie("ops_session");
            assertThat(oldCookie).isNotNull();
            long epoch = operators.sessionEpoch(username);
            mvc.perform(post("/ops/logout").with(csrf()).cookie(oldCookie))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops/login"));
            assertThat(operators.sessionEpoch(username)).isEqualTo(epoch + 1);
            mvc.perform(get("/ops").cookie(oldCookie))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops/login"));
            mvc.perform(post("/ops/logout").with(csrf()).cookie(oldCookie))
                    .andExpect(status().is3xxRedirection());
            assertThat(operators.sessionEpoch(username)).isEqualTo(epoch + 1);

            MvcResult fresh = mvc.perform(post("/ops/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "password1234"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops"))
                    .andReturn();
            Cookie current = fresh.getResponse().getCookie("ops_session");
            assertThat(current).isNotNull();
            mvc.perform(get("/ops").cookie(current)).andExpect(status().isOk());
            mvc.perform(get("/ops").cookie(oldCookie))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/ops/login"));
        } finally {
            if (before == null) {
                Files.deleteIfExists(file);
            } else {
                Files.write(file, before);
            }
        }
    }

    private Cookie register(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/account/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("student_session");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String tokenFor(String email) {
        Map<String, Object> issued = resets.requestReset(email, "127.0.0.1");
        String url = String.valueOf(issued.get("devResetUrl"));
        return url.substring(url.indexOf("token=") + 6);
    }

    private Timestamp usedAt(String studentId) {
        List<Timestamp> rows = jdbc.query(
                "SELECT used_at FROM password_reset_tokens WHERE student_id = ?",
                (rs, row) -> rs.getTimestamp("used_at"),
                studentId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}
