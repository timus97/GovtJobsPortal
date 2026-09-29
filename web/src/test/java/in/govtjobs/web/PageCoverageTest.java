package in.govtjobs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PageCoverageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private in.govtjobs.web.store.StudentStore students;

    @Autowired
    private in.govtjobs.web.store.JobStore jobs;

    @Test
    void publicPagesSendSecurityHeaders() throws Exception {
        mvc.perform(get("/jobs").param("q", "officer").param("page", "1").param("hasExam", "yes"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        mvc.perform(get("/jobs/does-not-exist")).andExpect(status().isOk());
        List<Map<String, Object>> rows = jobs.getJobs();
        if (!rows.isEmpty()) {
            mvc.perform(get("/jobs/" + rows.get(0).get("id"))).andExpect(status().isOk());
        }
        mvc.perform(get("/prepare")).andExpect(status().isOk());
        mvc.perform(get("/favicon.ico")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/favicon.svg"));
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/account/forgot")).andExpect(status().isOk());
        mvc.perform(get("/account/reset")).andExpect(status().isOk());
        mvc.perform(get("/match")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        mvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/ops")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/ops/review")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/ops/sources")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/ops/logs")).andExpect(status().is3xxRedirection());
    }

    @Test
    void signedInStudentCanSaveProfileMatchAndDesk() throws Exception {
        String email = "pages-" + UUID.randomUUID() + "@example.com";
        Cookie session = register(email);
        mvc.perform(get("/profile").cookie(session)).andExpect(status().isOk());
        mvc.perform(post("/profile")
                        .with(csrf())
                        .cookie(session)
                        .param("dob", "1998-04-12")
                        .param("highestEducation", "graduate")
                        .param("reservationCategory", "UR")
                        .param("birthState", "DL")
                        .param("domicileState", "DL")
                        .param("pwbdHas", "no"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?saved=1"));
        mvc.perform(get("/match").cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/dashboard").cookie(session)).andExpect(status().isOk());
        mvc.perform(post("/dashboard")
                        .with(csrf())
                        .cookie(session)
                        .param("title", "Walk-in")
                        .param("examDate", "2027-08-01")
                        .param("officialUrl", "https://ssc.gov.in/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        String sid = String.valueOf(students.findInternalByEmail(email).get("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) students.listItems(sid, null).get("items");
        String itemId = String.valueOf(items.get(0).get("id"));
        mvc.perform(get("/desk/" + itemId).cookie(session)).andExpect(status().isOk());
        mvc.perform(post("/desk/" + itemId).with(csrf()).cookie(session).param("status", "applied").param("notes", "form filled"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/desk/" + itemId + "/plan").cookie(session)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/desk/" + itemId + "/mock").cookie(session)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/account/logout").with(csrf()).cookie(session)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        long epoch = students.sessionEpoch(sid);
        students.setPassword(sid, "password5678");
        assertThat(students.sessionEpoch(sid)).isEqualTo(epoch + 1);
        mvc.perform(get("/dashboard").cookie(session)).andExpect(status().is3xxRedirection());
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
