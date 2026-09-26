package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.security.PasswordService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StudentStoreFileTest {

    @TempDir
    Path tmp;

    private StudentStore store;

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
        ObjectMapper mapper = new ObjectMapper();
        store = new StudentStore(
                mapper,
                new PasswordService(),
                new JobStore(mapper),
                new GovtJobsProperties(),
                new JdbcTemplate(ds),
                tmp.resolve("files"));
    }

    @Test
    void fileOpsRequireOwnedItemAndStayUnderFilesDir() throws Exception {
        Map<String, Object> owner = store.register("owner-" + java.util.UUID.randomUUID() + "@example.com", "password1234");
        Map<String, Object> other = store.register("other-" + java.util.UUID.randomUUID() + "@example.com", "password1234");
        String ownerId = String.valueOf(owner.get("id"));
        String otherId = String.valueOf(other.get("id"));
        Map<String, Object> item = store.createItem(
                ownerId,
                Map.of("kind", "custom", "title", "SSC CGL", "examDate", "2027-01-01", "officialUrl", "https://ssc.gov.in/"));
        String itemId = String.valueOf(item.get("id"));
        byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34};
        store.saveFile(ownerId, itemId, "admit", "admit.pdf", pdf);

        Path stored = store.resolveFile(ownerId, itemId, "admit");
        assertThat(stored).isNotNull();
        assertThat(stored.startsWith(tmp.resolve("files").toAbsolutePath().normalize())).isTrue();
        assertThat(stored.getFileName().toString()).isEqualTo("admit.pdf");

        assertThat(store.resolveFile(otherId, itemId, "admit")).isNull();
        assertThatThrownBy(() -> store.saveFile(otherId, itemId, "admit", "x.pdf", pdf))
                .isInstanceOf(StoreException.class);
        assertThat(store.deleteFile(otherId, itemId, "admit")).isNull();
        assertThat(Files.isRegularFile(stored)).isTrue();

        store.deleteItem(ownerId, itemId);
        assertThat(Files.exists(tmp.resolve("files").resolve(ownerId).resolve(itemId))).isFalse();
    }

    @Test
    void createItemRejectsUnknownRefAndNonHttps() {
        Map<String, Object> owner = store.register("refs-" + java.util.UUID.randomUUID() + "@example.com", "password1234");
        String ownerId = String.valueOf(owner.get("id"));
        assertThatThrownBy(() -> store.createItem(ownerId, Map.of("kind", "series", "refId", "missing-series")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Exam series");
        assertThatThrownBy(() -> store.createItem(
                        ownerId,
                        Map.of(
                                "kind",
                                "custom",
                                "title",
                                "X",
                                "examDate",
                                "2027-01-01",
                                "officialUrl",
                                "http://ssc.gov.in/")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(() -> store.createItem(ownerId, Map.of("kind", "custom", "title", "  ")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> store.createItem(ownerId, Map.of("kind", "custom", "title", "No date")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("date");
        assertThatThrownBy(() -> store.createItem(
                        ownerId, Map.of("kind", "custom", "title", "X", "examDate", "2027-01-01", "status", "nope")))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("status");
    }

    @Test
    void duplicateEmailUsesGenericValidationMessage() {
        String email = "dup-" + java.util.UUID.randomUUID() + "@example.com";
        store.register(email, "password1234");
        assertThatThrownBy(() -> store.register(email, "password1234"))
                .isInstanceOf(StoreException.class)
                .hasMessage("A valid email is required")
                .hasMessageNotContaining("already exists");
    }
}
