package in.govtjobs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest(properties = "govtjobs.catalog.source=postgres")
@AutoConfigureMockMvc
class CatalogLabelPageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String prefix;

    @BeforeEach
    void insertRows() {
        prefix = "slice8-" + UUID.randomUUID().toString().substring(0, 8);
        insert(prefix + "-blank", "Slice eight blank location", "");
        insert(prefix + "-india", "Slice eight stated location", "All India");
    }

    @AfterEach
    void deleteRows() {
        jdbc.update("DELETE FROM catalog.opportunities WHERE id LIKE ?", prefix + "%");
    }

    @Test
    void blankLocationSaysNotListedAndAStatedLocationStays() throws Exception {
        mvc.perform(get("/jobs/" + prefix + "-blank"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not listed.")))
                .andExpect(content().string(not(containsString("All India"))));
        mvc.perform(get("/jobs/" + prefix + "-india"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("All India")))
                .andExpect(content().string(not(containsString("Not listed."))));
        mvc.perform(get("/jobs").param("q", "Slice eight blank location"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not listed.")));
    }

    private void insert(String id, String title, String location) {
        jdbc.update(
                """
                INSERT INTO catalog.opportunities
                  (id, title, organization, org_type, sector, location, qualification, selection_process,
                   has_exam, last_date, official_url, source_name, review_status, summary, sample)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'Slice 8',?,'',FALSE)
                """,
                id,
                title,
                "Staff Selection Commission",
                "central",
                "Other",
                location,
                "graduate",
                "cbt",
                true,
                java.sql.Date.valueOf("2031-02-02"),
                "https://ssc.gov.in/" + id,
                "approved");
    }
}
