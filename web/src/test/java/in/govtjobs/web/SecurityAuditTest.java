package in.govtjobs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.govtjobs.web.store.StudentStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuditTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StudentStore students;

    @Test
    void publicCatalogDoesNotRequireLogin() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isOk());
    }

    @Test
    void dashboardRequiresStudent() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void registerSetsHttpOnlyStudentCookieWithoutPassword() throws Exception {
        String email = "audit-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/account/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "password1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("student_session");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getValue()).doesNotContain("password1234");
        Cookie ops = result.getResponse().getCookie("ops_session");
        if (ops != null) {
            assertThat(ops.getMaxAge()).isZero();
        }
        SecurityContext ctx = (SecurityContext)
                result.getRequest().getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (ctx != null && ctx.getAuthentication() != null) {
            assertThat(ctx.getAuthentication().getCredentials()).isNull();
        }
    }

    @Test
    void studentCannotDownloadAnotherStudentsFile() throws Exception {
        String emailA = "idor-a-" + UUID.randomUUID() + "@example.com";
        String emailB = "idor-b-" + UUID.randomUUID() + "@example.com";
        Cookie sessionA = register(emailA);
        Cookie sessionB = register(emailB);
        mvc.perform(post("/dashboard")
                        .with(csrf())
                        .cookie(sessionA)
                        .param("title", "Custom exam")
                        .param("examDate", "2027-06-01")
                        .param("officialUrl", "https://ssc.gov.in/"))
                .andExpect(status().is3xxRedirection());
        String sidA = String.valueOf(students.findInternalByEmail(emailA).get("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) students.listItems(sidA, null).get("items");
        assertThat(items).isNotEmpty();
        String itemId = String.valueOf(items.get(0).get("id"));
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "admit.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34});
        mvc.perform(multipart("/desk/" + itemId + "/files/admit").file(pdf).with(csrf()).cookie(sessionA))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/desk/" + itemId + "/files/admit").cookie(sessionB)).andExpect(status().isNotFound());
        mvc.perform(get("/desk/" + itemId + "/files/admit").cookie(sessionA)).andExpect(status().isOk());
    }

    @Test
    void opsLoginPageIncludesCsrf() throws Exception {
        mvc.perform(get("/ops/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void studentCannotPostOpsOperator() throws Exception {
        mvc.perform(post("/ops/operators")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .param("username", "intruder")
                        .param("password", "password1234"))
                .andExpect(status().isForbidden());
    }

    private Cookie register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/account/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "password1234"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("student_session");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
