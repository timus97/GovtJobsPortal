package in.govtjobs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PreparePageTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void syncedCalendarsShowProseDatesEducationWordsAndNoApplyOnScoreExams() throws Exception {
        mvc.perform(get("/prepare"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("May 2026")))
                .andExpect(content().string(containsString("April-May,2024")))
                .andExpect(content().string(containsString("Expected exam (not a single day)")))
                .andExpect(content().string(not(containsString("Expected notification"))))
                .andExpect(content().string(not(containsString("Expected apply window"))))
                .andExpect(content().string(containsString("No notification, apply, or exam date is on file.")))
                .andExpect(content().string(containsString(">Graduate<")))
                .andExpect(content().string(containsString(">12th<")))
                .andExpect(content().string(not(containsString(">graduate<"))))
                .andExpect(content().string(containsString("Not a vacancy")))
                .andExpect(content().string(containsString(">Show calendars</button>")))
                .andExpect(content().string(containsString("Sign in to track")))
                .andExpect(content().string(not(containsString("onchange"))))
                .andExpect(content().string(not(containsString("Apply (open window)"))));
    }

    @Test
    void unknownBoardSaysNoCalendarsMatch() throws Exception {
        mvc.perform(get("/prepare").param("board", "not-a-board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No calendars match")))
                .andExpect(content().string(containsString(">Show calendars</button>")))
                .andExpect(content().string(containsString("value=\"not-a-board\"")))
                .andExpect(content().string(not(containsString("May 2026"))));
        mvc.perform(get("/prepare").param("board", "SSC"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("May 2026")))
                .andExpect(content().string(containsString("April-May,2024")))
                .andExpect(content().string(not(containsString("No calendars match"))))
                .andExpect(content().string(not(containsString("Not a vacancy"))));
    }
}
