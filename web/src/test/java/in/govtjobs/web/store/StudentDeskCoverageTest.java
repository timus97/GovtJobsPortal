package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.security.PasswordService;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StudentDeskCoverageTest {

    @TempDir
    Path tmp;

    private StudentStore store;
    private String studentId;

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
        store = new StudentStore(
                new ObjectMapper(),
                new PasswordService(),
                new JobStore(new ObjectMapper()),
                new GovtJobsProperties(),
                new JdbcTemplate(ds),
                tmp);
        Map<String, Object> student =
                store.register("desk-" + UUID.randomUUID() + "@example.com", "password1234");
        studentId = String.valueOf(student.get("id"));
    }

    @Test
    void registerVerifyAndProfile() {
        assertThat(store.backend()).isEqualTo("postgres");
        assertThat(store.studentCount()).isPositive();
        assertThatThrownBy(() -> store.register("not-an-email", "password1234"))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.register("short-" + UUID.randomUUID() + "@example.com", "short"))
                .isInstanceOf(StoreException.class);
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        store.register(email, "password1234");
        assertThatThrownBy(() -> store.register(email, "password1234")).isInstanceOf(StoreException.class);
        assertThat(store.verify(email, "wrong-password")).isNull();
        assertThat(store.verify(email, "password1234")).isNotNull();
        assertThat(store.findById("missing")).isNull();
        assertThat(store.findInternalByEmail("  ")).isNull();
        assertThat(store.getProfile(studentId)).isNull();
        assertThat(store.saveProfile(studentId, null)).containsKey("updatedAt");
        assertThat(store.saveProfile(studentId, Map.of("name", "Ada"))).containsEntry("name", "Ada");
        assertThat(store.getProfile(studentId)).containsEntry("name", "Ada");
        assertThat(store.saveProfile("missing-student", Map.of("name", "x"))).isNull();
        assertThatThrownBy(() -> store.setPassword(studentId, "short")).isInstanceOf(StoreException.class);
        assertThat(store.setPassword("missing-student", "password1234")).isNull();
    }

    @Test
    void deskItemsFilesAndMocks() throws Exception {
        assertThatThrownBy(() -> store.createItem("missing", Map.of("kind", "custom", "title", "T", "examDate", "2027-01-01")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.createItem(studentId, Map.of("kind", "nope", "title", "T")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.createItem(studentId, Map.of("kind", "custom", "title", "T", "status", "nope")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.createItem(studentId, Map.of("kind", "custom", "title", "  ")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.createItem(studentId, Map.of("kind", "custom", "title", "Walk-in")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.createItem(
                        studentId, Map.of("kind", "custom", "title", "Walk-in", "examDate", "2027-01-01", "officialUrl", "http://ssc.gov.in")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.createItem(studentId, Map.of("kind", "series")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.createItem(studentId, Map.of("kind", "series", "refId", "missing-series")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.listItems(studentId, "nope")).isInstanceOf(StoreException.class);

        Map<String, Object> item = store.createItem(
                studentId,
                Map.of(
                        "kind",
                        "custom",
                        "title",
                        "Walk-in",
                        "examDate",
                        "2027-04-01",
                        "officialUrl",
                        "https://ssc.gov.in/notice",
                        "notes",
                        "bring id",
                        "board",
                        "SSC"));
        String itemId = String.valueOf(item.get("id"));
        assertThat(store.getItem(studentId, itemId)).containsEntry("title", "Walk-in");
        assertThat(store.getItem(studentId, "missing")).isNull();
        Map<String, Object> listed = store.listItems(studentId, "watching");
        assertThat(listed.get("total")).isEqualTo(1);

        assertThat(store.patchItem(studentId, "missing", Map.of("notes", "x"))).isNull();
        assertThatThrownBy(() -> store.patchItem(studentId, itemId, Map.of("status", "nope")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.patchItem(studentId, itemId, Map.of("officialUrl", "http://bad.example")))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.patchItem(studentId, itemId, Map.of("title", "  ")))
                .isInstanceOf(StoreException.class);
        Map<String, Object> patched = store.patchItem(
                studentId,
                itemId,
                Map.of("status", "applied", "notes", "done", "title", "Walk-in updated", "board", "SSC", "lastDate", "2027-03-01"));
        assertThat(patched).containsEntry("status", "applied");

        byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2d};
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        assertThatThrownBy(() -> store.saveFile(studentId, itemId, "other", "a.pdf", pdf))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.saveFile(studentId, itemId, "admit", "a.pdf", new byte[0]))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.saveFile(studentId, itemId, "admit", "a.txt", new byte[] {1, 2, 3, 4}))
                .isInstanceOf(StoreException.class);
        store.saveFile(studentId, itemId, "admit", "card.pdf", pdf);
        store.saveFile(studentId, itemId, "result", null, jpeg);
        store.saveFile(studentId, itemId, "result", "shot.png", png);
        assertThat(store.fileMeta(studentId, itemId, "admit")).isNotNull();
        assertThat(store.resolveFile(studentId, itemId, "admit")).isNotNull();
        assertThat(store.deleteFile(studentId, itemId, "admit")).isNotNull();
        assertThat(store.fileMeta(studentId, "missing", "admit")).isNull();

        store.setTopicDone(studentId, "series-1", "topic-1", true);
        store.setTopicDone(studentId, "series-1", "topic-1", true);
        assertThat(store.topicProgress(studentId, "series-1")).containsKey("topic-1");
        store.setTopicDone(studentId, "series-1", "topic-1", false);
        assertThat(store.topicProgress(studentId, "series-1")).doesNotContainKey("topic-1");

        Map<String, Object> attempt = store.openAttempt(studentId, "series-1", itemId);
        assertThat(store.openAttempt(studentId, "series-1", itemId)).containsEntry("id", attempt.get("id"));
        assertThat(store.getAttempt(studentId, "missing")).isNull();
        assertThat(store.submitAttempt(studentId, "missing", Map.of(), 1, 2)).isNull();
        String attemptId = String.valueOf(attempt.get("id"));
        assertThat(store.submitAttempt(studentId, attemptId, Map.of("q1", "a"), 1, 2)).containsEntry("score", 1);
        assertThatThrownBy(() -> store.submitAttempt(studentId, attemptId, null, 1, 2))
                .isInstanceOf(StoreException.class);

        assertThat(store.listItems(studentId, null).get("total")).isEqualTo(1);
        assertThat(store.deleteItem(studentId, itemId)).isTrue();
        assertThat(store.deleteItem(studentId, itemId)).isFalse();
        assertThat(store.filesDir()).isEqualTo(tmp);
    }
}
