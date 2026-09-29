package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.security.PasswordService;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StudentStoreMoreTest {

    @TempDir
    java.nio.file.Path tmp;

    private StudentStore store;
    private JobStore jobs;
    private JdbcTemplate jdbc;
    private GovtJobsProperties props;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:postgresql://127.0.0.1:5432/govtjobs_students");
        ds.setUsername("govtjobs");
        ds.setPassword("govtjobs");
        ds.setDriverClassName("org.postgresql.Driver");
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(ds);
        ObjectMapper mapper = new ObjectMapper();
        props = new GovtJobsProperties();
        props.getStudent().setImportJson(false);
        jobs = new JobStore(mapper);
        store = new StudentStore(
                mapper, new PasswordService(), jobs, props, jdbc, tmp.resolve("files"));
    }

    @Test
    void registerVerifyPasswordAndEpoch() {
        assertThat(store.backend()).isEqualTo("postgres");
        assertThatThrownBy(() -> store.register("  ", "password1234"))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("email");
        assertThatThrownBy(() -> store.register("not-an-email", "password1234"))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.register(email(), null))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("10");
        assertThatThrownBy(() -> store.register(email(), "short"))
                .isInstanceOf(StoreException.class);

        String email = "MiXeD-" + UUID.randomUUID() + "@Example.com";
        Map<String, Object> created = store.register(email, "password1234");
        String id = String.valueOf(created.get("id"));
        assertThat(created.get("email")).isEqualTo(email.trim());
        assertThat(created).doesNotContainKey("passwordHash");
        assertThat(store.findById(id).get("email")).isEqualTo(email.trim());
        assertThat(store.findById(null)).isNull();
        assertThat(store.findById(" ")).isNull();
        assertThat(store.findById("missing-" + id)).isNull();
        assertThat(store.findInternalByEmail(null)).isNull();
        assertThat(store.findInternalByEmail("   ")).isNull();
        assertThat(store.sessionEpoch(null)).isZero();
        assertThat(store.sessionEpoch("")).isZero();
        assertThat(store.sessionEpoch("missing-" + id)).isZero();
        assertThat(store.sessionEpoch(id)).isZero();
        assertThat(store.verify(email, "wrong-password")).isNull();
        assertThat(store.verify("  ", "password1234")).isNull();
        assertThat(store.verify(email, "password1234").get("id")).isEqualTo(id);

        assertThat(store.setPassword(null, "password1234")).isNull();
        assertThat(store.setPassword("missing-" + id, "password1234")).isNull();
        assertThatThrownBy(() -> store.setPassword(id, "short"))
                .isInstanceOf(StoreException.class);
        assertThat(store.setPassword(id, "new-password").get("id")).isEqualTo(id);
        assertThat(store.sessionEpoch(id)).isEqualTo(1);
        assertThat(store.verify(email, "password1234")).isNull();
        assertThat(store.verify(email, "new-password")).isNotNull();
        store.setPassword(id, "newer-password");
        assertThat(store.sessionEpoch(id)).isEqualTo(2);

        props.getStudent().setPasswordMin(0);
        assertThatThrownBy(() -> store.setPassword(id, "tiny"))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining(String.valueOf(StudentStore.MIN_PASSWORD));
    }

    @Test
    void profileSaveUpdateAndUnreadableJson() {
        String id = idOf(store.register(email(), "password1234"));
        assertThat(store.getProfile(id)).isNull();
        assertThat(store.saveProfile("missing-" + id, Map.of("name", "No"))).isNull();
        Map<String, Object> saved = store.saveProfile(id, null);
        assertThat(saved).containsKey("updatedAt");
        assertThat(store.getProfile(id)).containsKey("updatedAt");
        Map<String, Object> next = store.saveProfile(id, Map.of("fullName", "A Student", "category", "GEN"));
        assertThat(next.get("fullName")).isEqualTo("A Student");
        assertThat(store.getProfile(id)).containsEntry("category", "GEN").containsEntry("fullName", "A Student");

        jdbc.update("UPDATE student_profiles SET profile = '[]'::jsonb WHERE student_id = ?", id);
        assertThatThrownBy(() -> store.getProfile(id))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Profile");
    }

    @Test
    void itemsProfileFiltersPatchAndCatalogRefs() {
        String id = idOf(store.register(email(), "password1234"));
        assertThatThrownBy(() -> store.createItem("missing-" + id, Map.of("kind", "custom", "title", "X", "examDate", "2027-01-01")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("signed in");
        assertThatThrownBy(() -> store.createItem(id, Map.of("kind", "nope")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("kind");
        assertThatThrownBy(() -> store.createItem(id, Map.of("kind", "series", "refId", "  ")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("refId");
        assertThatThrownBy(() -> store.createItem(id, Map.of("kind", "series")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("refId");
        assertThatThrownBy(() -> store.createItem(id, Map.of("kind", "opportunity", "refId", "missing-job")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Job not found");
        assertThatThrownBy(() -> store.createItem(id, Map.of("kind", "series", "refId", "missing-series")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Exam series");
        assertThatThrownBy(() -> store.listItems(id, "nope"))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("status");

        Map<String, Object> custom = store.createItem(id, Map.of(
                "title", "Future exam",
                "examDate", "2027-08-01",
                "officialUrl", "null",
                "notes", "  bring id  ",
                "status", "applied"));
        String customId = String.valueOf(custom.get("id"));
        assertThat(custom.get("officialUrl")).isEqualTo("");
        assertThat(custom.get("status")).isEqualTo("applied");
        assertThat(custom.get("hasAdmit")).isEqualTo(false);
        Map<String, Object> past = store.createItem(id, Map.of(
                "kind", "custom",
                "title", "Past exam",
                "lastDate", "2020-01-01",
                "board", "Local",
                "officialUrl", "https://ssc.gov.in/notice"));
        assertThat(store.getItem(id, String.valueOf(past.get("id"))).get("title")).isEqualTo("Past exam");
        assertThat(store.getItem(id, "missing-item")).isNull();

        Map<String, Object> listed = store.listItems(id, " ");
        assertThat(listed.get("total")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) listed.get("stats");
        assertThat(stats.get("admitPending")).isEqualTo(1L);
        assertThat(((Number) stats.get("upcoming")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(store.listItems(id, "applied").get("total")).isEqualTo(1);
        assertThat(store.listItems(id, "watching").get("total")).isEqualTo(1);

        assertThat(store.patchItem(id, "missing-item", Map.of("notes", "x"))).isNull();
        Map<String, Object> patched = store.patchItem(id, customId, null);
        assertThat(patched.get("id")).isEqualTo(customId);
        assertThatThrownBy(() -> store.patchItem(id, customId, Map.of("status", "nope")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.patchItem(id, customId, Map.of("title", "  ")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> store.patchItem(id, customId, Map.of("officialUrl", "http://ssc.gov.in/")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("https");
        Map<String, Object> updated = store.patchItem(id, customId, Map.of(
                "title", "Future renamed",
                "board", "SSC",
                "status", "watching",
                "examDate", "2027-09-01",
                "lastDate", "",
                "officialUrl", "https://ssc.gov.in/next",
                "notes", "updated"));
        assertThat(updated.get("title")).isEqualTo("Future renamed");
        assertThat(updated.get("board")).isEqualTo("SSC");
        assertThat(updated.get("notes")).isEqualTo("updated");
        assertThat(updated.get("officialUrl")).isEqualTo("https://ssc.gov.in/next");

        var series = jobs.getExamSeries().stream().findFirst();
        if (series.isPresent()) {
            String ref = String.valueOf(series.get().get("id"));
            Map<String, Object> first = store.createItem(id, Map.of("kind", "series", "refId", ref, "status", "watching"));
            assertThat(first.get("kind")).isEqualTo("series");
            assertThat(first.get("title")).isNotNull();
            Map<String, Object> same = store.createItem(id, Map.of("kind", "series", "refId", ref, "status", "watching"));
            assertThat(same.get("id")).isEqualTo(first.get("id"));
            Map<String, Object> moved = store.createItem(id, Map.of("kind", "series", "refId", ref, "status", "applied"));
            assertThat(moved.get("status")).isEqualTo("applied");
            assertThat(store.patchItem(id, String.valueOf(first.get("id")), Map.of("title", "ignored", "board", "ignored"))
                    .get("kind")).isEqualTo("series");
        }
        var job = jobs.getJobs().stream().findFirst();
        if (job.isPresent()) {
            String ref = String.valueOf(job.get().get("id"));
            Map<String, Object> card = store.createItem(id, Map.of("kind", "opportunity", "refId", ref));
            assertThat(card.get("kind")).isEqualTo("opportunity");
            assertThat(card.get("refId")).isEqualTo(ref);
        }

        assertThat(store.deleteItem(id, "missing-item")).isFalse();
        assertThat(store.deleteItem(id, customId)).isTrue();
        assertThat(store.getItem(id, customId)).isNull();
    }

    @Test
    void filesTopicsAndAttempts() throws Exception {
        String id = idOf(store.register(email(), "password1234"));
        Map<String, Object> item = store.createItem(id, Map.of(
                "kind", "custom", "title", "Files", "examDate", "2027-03-01", "status", "applied"));
        String itemId = String.valueOf(item.get("id"));
        byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34};
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        assertThatThrownBy(() -> store.saveFile(id, itemId, "photo", "a.pdf", pdf))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("admit or result");
        assertThatThrownBy(() -> store.saveFile(id, itemId, "admit", "a.pdf", new byte[0]))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("5 MB");
        assertThatThrownBy(() -> store.saveFile(id, itemId, "admit", "a.pdf", null))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.saveFile(id, itemId, "admit", "a.pdf", new byte[] {1, 2, 3, 4}))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("PNG");
        props.getStudent().setMaxFileBytes(4);
        assertThatThrownBy(() -> store.saveFile(id, itemId, "admit", "a.pdf", pdf))
                .isInstanceOf(StoreException.class);
        props.getStudent().setMaxFileBytes(0);
        store.saveFile(id, itemId, "admit", "C:\\cards\\my admit.pdf", pdf);
        assertThat(store.fileMeta(id, itemId, "admit").get("originalName").toString()).contains("admit");
        store.saveFile(id, itemId, "admit", "!!!", jpeg);
        assertThat(store.fileMeta(id, itemId, "admit").get("mime")).isEqualTo("image/jpeg");
        store.saveFile(id, itemId, "result", "a".repeat(90) + ".png", png);
        assertThat(store.resolveFile(id, itemId, "result")).isNotNull();
        assertThat(store.resolveFile(id, itemId, "missing")).isNull();
        assertThat(store.fileMeta(id, "missing-item", "admit")).isNull();
        assertThat(store.deleteFile(id, itemId, "admit").get("hasAdmit")).isEqualTo(false);
        assertThat(store.deleteFile(id, "missing-item", "admit")).isNull();

        store.setTopicDone(id, "series-1", "topic-1", true);
        assertThat(store.topicProgress(id, "series-1")).containsKey("topic-1");
        store.setTopicDone(id, "series-1", "topic-1", false);
        assertThat(store.topicProgress(id, "series-1")).isEmpty();
        store.setTopicDone(id, "series-1", "topic-1", false);

        assertThat(store.getAttempt(id, "missing")).isNull();
        Map<String, Object> attempt = store.openAttempt(id, "series-1", null);
        Map<String, Object> again = store.openAttempt(id, "series-1", itemId);
        assertThat(again.get("id")).isEqualTo(attempt.get("id"));
        Map<String, Object> submitted = store.submitAttempt(id, String.valueOf(attempt.get("id")), null, 3, 5);
        assertThat(submitted.get("score")).isEqualTo(3);
        assertThat(submitted.get("answers")).isNull();
        assertThatThrownBy(() -> store.submitAttempt(id, String.valueOf(attempt.get("id")), Map.of("q", "a"), 3, 5))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("submitted");
        assertThat(store.submitAttempt(id, "missing", Map.of(), 0, 1)).isNull();
        Map<String, Object> next = store.openAttempt(id, "series-1", itemId);
        assertThat(next.get("id")).isNotEqualTo(attempt.get("id"));
        store.submitAttempt(id, String.valueOf(next.get("id")), Map.of("q", "b"), 1, 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) store.listItems(id, null).get("stats");
        assertThat(stats.get("mocksCompleted")).isEqualTo(2L);
    }

    private static String email() {
        return "more-" + UUID.randomUUID() + "@example.com";
    }

    private static String idOf(Map<String, Object> student) {
        return String.valueOf(student.get("id"));
    }
}
