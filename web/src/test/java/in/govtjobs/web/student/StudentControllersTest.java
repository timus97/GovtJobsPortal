package in.govtjobs.web.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.store.StoreException;
import in.govtjobs.web.store.StudentStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class StudentControllersTest {

    @Test
    void profileFormAndSaveCoverDomicileAndPwbd() {
        StudentStore students = mock(StudentStore.class);
        when(students.findInternalById("stu-1")).thenReturn(Map.of("id", "stu-1"));
        when(students.getProfile("stu-1")).thenReturn(null);
        ProfileController controller = new ProfileController(students, new CurrentStudent(students));
        ExtendedModelMap empty = new ExtendedModelMap();
        assertThat(controller.form(student(), empty)).isEqualTo("student/profile");
        assertThat(empty.getAttribute("domicileState")).isEqualTo("");
        assertThat(empty.getAttribute("states")).isNotNull();
        assertThat(empty.getAttribute("complete")).isEqualTo(false);

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("reservationCategory", "UR");
        stored.put("dob", "2000-01-01");
        stored.put("highestEducation", "graduate");
        stored.put("domicileStates", List.of("DL"));
        when(students.getProfile("stu-1")).thenReturn(stored);
        ExtendedModelMap filled = new ExtendedModelMap();
        controller.form(student(), filled);
        assertThat(filled.getAttribute("domicileState")).isEqualTo("DL");
        assertThat(filled.getAttribute("complete")).isEqualTo(true);

        stored.put("domicileStates", List.of());
        controller.form(student(), new ExtendedModelMap());
        stored.put("domicileStates", "DL");
        controller.form(student(), new ExtendedModelMap());
        stored.put("domicileStates", List.of());
        stored.put("domicileStates", java.util.Arrays.asList((Object) null));
        ExtendedModelMap nullFirst = new ExtendedModelMap();
        controller.form(student(), nullFirst);
        assertThat(nullFirst.getAttribute("domicileState")).isEqualTo("");

        assertThat(controller.save(student(), "2000-01-01", "graduate", "UR", "DL", "  ", "eng", "female", "yes", "OH", new ExtendedModelMap()))
                .isEqualTo("redirect:/profile?saved=1");
        assertThat(controller.save(student(), "2000-01-01", "graduate", "UR", "DL", "MH", null, null, "no", null, new ExtendedModelMap()))
                .isEqualTo("redirect:/profile?saved=1");
        assertThat(controller.save(student(), "2000-01-01", "graduate", "UR", "DL", null, null, null, null, null, new ExtendedModelMap()))
                .isEqualTo("redirect:/profile?saved=1");
        verify(students, org.mockito.Mockito.atLeastOnce()).saveProfile(eq("stu-1"), any());
    }

    @Test
    void deskCoversListEditUploadAndDownload(@TempDir Path dir) throws Exception {
        StudentStore students = mock(StudentStore.class);
        when(students.findInternalById("stu-1")).thenReturn(Map.of("id", "stu-1"));
        when(students.listItems("stu-1", null)).thenReturn(Map.of("items", List.of()));
        when(students.getItem("stu-1", "missing")).thenReturn(null);
        when(students.getItem("stu-1", "item-1")).thenReturn(Map.of("id", "item-1", "title", "Watch"));
        DeskController controller = new DeskController(students, new CurrentStudent(students));

        ExtendedModelMap dash = new ExtendedModelMap();
        assertThat(controller.dashboard(student(), null, dash)).isEqualTo("desk/dashboard");
        assertThat(dash.getAttribute("status")).isEqualTo("");
        ExtendedModelMap filtered = new ExtendedModelMap();
        controller.dashboard(student(), "watching", filtered);
        assertThat(filtered.getAttribute("status")).isEqualTo("watching");

        assertThat(controller.addCustom(student(), "My exam", "SSC", "2099-01-01", "https://ssc.gov.in/"))
                .isEqualTo("redirect:/dashboard");
        assertThat(controller.track(student(), "series", "ssc-cgl", "watching")).isEqualTo("redirect:/dashboard");
        assertThat(controller.detail(student(), "missing", new ExtendedModelMap())).isEqualTo("redirect:/dashboard");
        ExtendedModelMap detail = new ExtendedModelMap();
        assertThat(controller.detail(student(), "item-1", detail)).isEqualTo("desk/detail");
        assertThat(detail.getAttribute("item")).isNotNull();

        assertThat(controller.update(student(), "item-1", "applied", "2099-02-01", "2099-01-01", "note", null))
                .isEqualTo("redirect:/desk/item-1");
        assertThat(controller.update(student(), "item-1", null, null, null, null, "delete"))
                .isEqualTo("redirect:/dashboard");
        verify(students).deleteItem("stu-1", "item-1");

        MockMultipartFile file = new MockMultipartFile("file", "admit.pdf", "application/pdf", new byte[] {1, 2, 3});
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        assertThat(controller.upload(student(), "item-1", "admit", file, redirect)).isEqualTo("redirect:/desk/item-1");
        when(students.saveFile(any(), any(), any(), any(), any())).thenThrow(new StoreException("VALIDATION", "Too large"));
        RedirectAttributesModelMap bad = new RedirectAttributesModelMap();
        controller.upload(student(), "item-1", "admit", file, bad);
        assertThat(bad.getFlashAttributes().get("notice")).isEqualTo("Too large");

        assertThat(controller.download(student(), "item-1", "admit").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        when(students.resolveFile("stu-1", "item-1", "admit")).thenReturn(dir.resolve("missing.pdf"));
        assertThat(controller.download(student(), "item-1", "admit").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Path admit = dir.resolve("admit.pdf");
        Files.write(admit, new byte[] {'%', 'P', 'D', 'F'});
        when(students.resolveFile("stu-1", "item-1", "admit")).thenReturn(admit);
        when(students.fileMeta("stu-1", "item-1", "admit")).thenReturn(null);
        ResponseEntity<Resource> plain = controller.download(student(), "item-1", "admit");
        assertThat(plain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(plain.getHeaders().getContentType().toString()).contains("application/octet-stream");

        when(students.fileMeta("stu-1", "item-1", "admit"))
                .thenReturn(Map.of("originalName", "my\"card.pdf", "mime", "application/pdf"));
        ResponseEntity<Resource> named = controller.download(student(), "item-1", "admit");
        assertThat(named.getHeaders().getFirst("Content-Disposition")).contains("mycard.pdf");
        assertThat(named.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(named.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");

        when(students.fileMeta("stu-1", "item-1", "admit")).thenReturn(Map.of("mime", "not a mime"));
        ResponseEntity<Resource> badMime = controller.download(student(), "item-1", "admit");
        assertThat(badMime.getHeaders().getContentType().toString()).contains("application/octet-stream");

        assertThat(controller.deleteFile(student(), "item-1", "admit")).isEqualTo("redirect:/desk/item-1");
        verify(students).deleteFile("stu-1", "item-1", "admit");
    }

    @Test
    void coachingPlanMockAndReviewUseLocalPacksOrRedirect() {
        StudentStore students = mock(StudentStore.class);
        when(students.findInternalById("stu-1")).thenReturn(Map.of("id", "stu-1"));
        CoachingController controller = new CoachingController(students, new ObjectMapper(), new CurrentStudent(students));

        when(students.getItem("stu-1", "gone")).thenReturn(null);
        assertThat(controller.plan(student(), "gone", new ExtendedModelMap())).isEqualTo("redirect:/dashboard");
        when(students.getItem("stu-1", "custom")).thenReturn(Map.of("id", "custom", "kind", "custom"));
        assertThat(controller.plan(student(), "custom", new ExtendedModelMap())).isEqualTo("redirect:/desk/custom");

        when(students.getItem("stu-1", "bad")).thenReturn(Map.of("id", "bad", "kind", "series", "refId", "../ssc"));
        ExtendedModelMap missingPack = new ExtendedModelMap();
        assertThat(controller.plan(student(), "bad", missingPack)).isEqualTo("desk/plan");
        assertThat(missingPack.getAttribute("missing")).isEqualTo(true);

        Map<String, Object> series = new LinkedHashMap<>();
        series.put("id", "desk-1");
        series.put("kind", "series");
        series.put("refId", "ssc-cgl");
        series.put("examDate", "2099-06-01");
        when(students.getItem("stu-1", "desk-1")).thenReturn(series);
        when(students.topicProgress("stu-1", "ssc-cgl")).thenReturn(Map.of("quant", "done"));
        ExtendedModelMap plan = new ExtendedModelMap();
        assertThat(controller.plan(student(), "desk-1", plan)).isEqualTo("desk/plan");
        assertThat(plan.getAttribute("plan")).isNotNull();
        assertThat(plan.getAttribute("seriesId")).isEqualTo("ssc-cgl");

        assertThat(controller.tick(student(), "gone", "quant", true)).isEqualTo("redirect:/desk/gone/plan");
        Map<String, Object> noRef = Map.of("id", "noref", "kind", "series");
        when(students.getItem("stu-1", "noref")).thenReturn(noRef);
        controller.tick(student(), "noref", "quant", false);
        controller.tick(student(), "desk-1", "quant", true);
        verify(students).setTopicDone("stu-1", "ssc-cgl", "quant", true);

        assertThat(controller.mock(student(), "gone", new ExtendedModelMap())).isEqualTo("redirect:/dashboard");
        assertThat(controller.mock(student(), "noref", new ExtendedModelMap())).isEqualTo("redirect:/desk/noref");
        ExtendedModelMap mockMissing = new ExtendedModelMap();
        assertThat(controller.mock(student(), "bad", mockMissing)).isEqualTo("desk/mock");
        assertThat(mockMissing.getAttribute("missing")).isEqualTo(true);

        when(students.openAttempt("stu-1", "ssc-cgl", "desk-1")).thenReturn(Map.of("id", "attempt-1"));
        ExtendedModelMap mock = new ExtendedModelMap();
        assertThat(controller.mock(student(), "desk-1", mock)).isEqualTo("desk/mock");
        assertThat(mock.getAttribute("bank")).isNotNull();
        assertThat(mock.getAttribute("attempt")).isNotNull();

        assertThat(controller.submit(student(), "gone", "attempt-1", Map.of("q_q1", "1"))).isEqualTo("redirect:/dashboard");
        assertThat(controller.submit(student(), "bad", "attempt-1", Map.of())).isEqualTo("redirect:/desk/bad");
        assertThat(controller.submit(student(), "desk-1", "attempt-1", Map.of("q_q1", "1", "other", "x")))
                .isEqualTo("redirect:/desk/desk-1/mock/attempt-1");
        verify(students).submitAttempt(eq("stu-1"), eq("attempt-1"), any(), any(Integer.class), any(Integer.class));

        when(students.getAttempt("stu-1", "missing-attempt")).thenReturn(null);
        assertThat(controller.review(student(), "desk-1", "missing-attempt", new ExtendedModelMap()))
                .isEqualTo("redirect:/dashboard");
        when(students.getAttempt("stu-1", "attempt-1")).thenReturn(Map.of("answers", "nope"));
        when(students.getItem("stu-1", "bad-bank")).thenReturn(Map.of("id", "bad-bank", "refId", "not-a-pack"));
        assertThat(controller.review(student(), "bad-bank", "attempt-1", new ExtendedModelMap()))
                .isEqualTo("redirect:/desk/bad-bank");

        ExtendedModelMap review = new ExtendedModelMap();
        assertThat(controller.review(student(), "desk-1", "attempt-1", review)).isEqualTo("desk/mock-review");
        assertThat(review.getAttribute("review")).isNotNull();

        when(students.getAttempt("stu-1", "attempt-2")).thenReturn(Map.of("answers", Map.of("q1", "1")));
        ExtendedModelMap reviewAnswers = new ExtendedModelMap();
        assertThat(controller.review(student(), "desk-1", "attempt-2", reviewAnswers)).isEqualTo("desk/mock-review");
    }

    private static UsernamePasswordAuthenticationToken student() {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "student@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        token.setDetails("stu-1");
        return token;
    }
}
