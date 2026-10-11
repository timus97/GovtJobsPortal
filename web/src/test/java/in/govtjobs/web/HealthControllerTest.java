package in.govtjobs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthReportsJavaRuntime() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("govt-jobs-portal"))
                .andExpect(jsonPath("$.runtime").value("java"))
                .andExpect(jsonPath("$.catalog.jobs").isNumber())
                .andExpect(jsonPath("$.catalog.examSeries").isNumber())
                .andExpect(jsonPath("$.studentStore.backend").value("postgres"))
                .andExpect(jsonPath("$.mail").isString())
                .andExpect(jsonPath("$.repoRoot").doesNotExist())
                .andExpect(jsonPath("$.studentStore.path").doesNotExist())
                .andExpect(jsonPath("$.studentStore.writable").doesNotExist())
                .andExpect(jsonPath("$.catalog.jobsPath").doesNotExist());
    }

    @Test
    void healthHidesDevMailFromARemoteCaller() throws Exception {
        ObjectMapper json = new ObjectMapper();
        MvcResult local = mvc.perform(get("/health").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult remote = mvc.perform(get("/health").with(request -> {
                    request.setRemoteAddr("203.0.113.10");
                    request.addHeader("X-Forwarded-For", "127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andReturn();
        String localMail = json.readTree(local.getResponse().getContentAsString()).get("mail").asText();
        String remoteMail = json.readTree(remote.getResponse().getContentAsString()).get("mail").asText();
        assertThat(localMail).isIn("dev", "resend", "smtp");
        if ("dev".equals(localMail)) {
            assertThat(remoteMail).isEqualTo("unconfigured");
        } else {
            assertThat(remoteMail).isEqualTo(localMail);
        }
        assertThat(remoteMail).isNotEqualTo("dev");
    }
}
