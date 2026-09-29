package in.govtjobs.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogPageTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void publicCatalogPagesRenderAndSecurityHeadersAreSet() throws Exception {
        mvc.perform(get("/jobs").param("q", "clerk").param("orgType", "central").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));

        mvc.perform(get("/jobs/does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));

        mvc.perform(get("/prepare").param("board", "SSC")).andExpect(status().isOk());
    }

    @Test
    void anonymousStudentAndOpsRoutesRedirect() throws Exception {
        mvc.perform(get("/match")).andExpect(status().isFound()).andExpect(redirectedUrl("/"));
        mvc.perform(get("/profile")).andExpect(status().isFound()).andExpect(redirectedUrl("/"));
        mvc.perform(get("/dashboard")).andExpect(status().isFound()).andExpect(redirectedUrl("/"));
        mvc.perform(get("/ops")).andExpect(status().isFound()).andExpect(redirectedUrl("/ops/login"));
    }

    @Test
    void faviconIcoRedirectsToSvg() throws Exception {
        mvc.perform(get("/favicon.ico"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/favicon.svg"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
    }
}
