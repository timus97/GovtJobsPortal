package in.govtjobs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.StudentStore;

@SpringBootTest
@AutoConfigureMockMvc
class TrackPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StudentStore students;

    @Autowired
    private JobStore jobs;

    @Test
    void signedOutJobAndPrepareDoNotPostTrack() throws Exception {
        mvc.perform(get("/jobs/" + liveJobId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in to track")))
                .andExpect(content().string(containsString("/account/login?next=")))
                .andExpect(content().string(not(containsString("action=\"/desk/track\""))))
                .andExpect(content().string(not(containsString(">I applied<"))));
        mvc.perform(get("/prepare"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in to track")))
                .andExpect(content().string(containsString("next=/prepare")))
                .andExpect(content().string(not(containsString("action=\"/desk/track\""))));
    }

    @Test
    void signedInTrackPostsAndSaysItDoesNotApply() throws Exception {
        mvc.perform(get("/jobs/" + liveJobId()).with(user("student@example.com").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/desk/track\"")))
                .andExpect(content().string(containsString(">I applied<")))
                .andExpect(content().string(containsString("This site does not apply for you.")))
                .andExpect(content().string(not(containsString("Sign in to track"))));
    }

    @Test
    void renamedTextFileStaysOnTheDeskItem() throws Exception {
        String email = "file-" + UUID.randomUUID() + "@example.com";
        Cookie session = register(email);
        postDashboard(session).andExpect(status().is3xxRedirection());
        String sid = String.valueOf(students.findInternalByEmail(email).get("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) students.listItems(sid, null).get("items");
        String itemId = String.valueOf(items.get(0).get("id"));
        MockMultipartFile text = new MockMultipartFile("file", "card.pdf", "application/pdf", "not a pdf".getBytes());
        MvcResult posted = mvc.perform(multipart("/desk/" + itemId + "/files/admit").file(text).with(csrf()).cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/desk/" + itemId))
                .andReturn();
        mvc.perform(get("/desk/" + itemId).cookie(session).flashAttrs(posted.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Only PDF, JPEG, and PNG are allowed")))
                .andExpect(content().string(containsString(">Admit card<")))
                .andExpect(content().string(containsString(">Result<")))
                .andExpect(content().string(containsString("5 MB at most")))
                .andExpect(content().string(containsString(">Watching</option>")))
                .andExpect(content().string(containsString(">Admit ready</option>")))
                .andExpect(content().string(containsString(">Result in</option>")))
                .andExpect(content().string(containsString(">Done</option>")))
                .andExpect(content().string(not(containsString("/plan"))))
                .andExpect(content().string(not(containsString("/mock"))));
    }

    private String liveJobId() {
        return String.valueOf(jobs.getJobs().get(0).get("id"));
    }

    private org.springframework.test.web.servlet.ResultActions postDashboard(Cookie session) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/dashboard")
                .with(csrf())
                .cookie(session)
                .param("title", "Walk-in")
                .param("examDate", "2027-08-01")
                .param("officialUrl", "https://ssc.gov.in/"));
    }

    private Cookie register(String email) throws Exception {
        MvcResult result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/account/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "password1234"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return result.getResponse().getCookie("student_session");
    }
}
