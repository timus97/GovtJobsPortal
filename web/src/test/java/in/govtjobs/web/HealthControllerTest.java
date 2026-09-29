package in.govtjobs.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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
}
