package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.security.PasswordService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperatorStoreTest {

    @TempDir
    Path tmp;

    @AfterEach
    void clearOverrides() {
        System.clearProperty("govtjobs.operator-store");
        System.clearProperty("govtjobs.operator-password");
    }

    @Test
    void createChecksUsernamePasswordAndOperatorRole() {
        storeAt(tmp.resolve("operators.json"));
        password(null);
        GovtJobsProperties props = new GovtJobsProperties();
        props.getStudent().setPasswordMin(12);
        OperatorStore store = new OperatorStore(new ObjectMapper(), new PasswordService(), props);
        assertThat(store.operators()).isEmpty();
        assertThat(store.findByUsername(null)).isNull();
        assertThat(store.verify("nobody", "password1234")).isFalse();
        assertThat(store.verify("nobody", null)).isFalse();

        String username = "op" + UUID.randomUUID().toString().substring(0, 8);
        assertThatThrownBy(() -> store.create("ab", "password123456"))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Username");
        assertThatThrownBy(() -> store.create("bad name", "password123456"))
                .isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.create(username, null))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("12");
        assertThatThrownBy(() -> store.create(username, "short-pass"))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("at least 12");

        var created = store.create(username, "password123456");
        assertThat(created.get("role")).isEqualTo("operator");
        assertThat(created).containsKeys("id", "username", "createdAt").doesNotContainKey("passwordHash");
        assertThat(store.verify(username, "password123456")).isTrue();
        assertThat(store.verify(username, "wrong-password")).isFalse();
        assertThat(store.findByUsername(username.toUpperCase()).get("role")).isEqualTo("operator");
        assertThatThrownBy(() -> store.create(username.toUpperCase(), "password123456"))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("already exists");
        assertThat(store.operators()).hasSize(1);
    }

    @Test
    void emptyStoreBootstrapsAdminOnlyWhenOperatorPasswordIsSet() {
        storeAt(tmp.resolve("boot.json"));
        password(null);
        OperatorStore empty = new OperatorStore(new ObjectMapper(), new PasswordService(), new GovtJobsProperties());
        assertThat(empty.operators()).isEmpty();

        password("bootstrap-pass");
        storeAt(tmp.resolve("admin.json"));
        OperatorStore store = new OperatorStore(new ObjectMapper(), new PasswordService(), null);
        assertThat(store.findByUsername("Admin").get("role")).isEqualTo("admin");
        assertThat(store.verify("admin", "bootstrap-pass")).isTrue();
        OperatorStore again = new OperatorStore(new ObjectMapper(), new PasswordService(), null);
        assertThat(again.operators()).hasSize(1);
    }

    @Test
    void zeroPasswordMinFallsBackAndCorruptOrUnwritableStoreFails() throws Exception {
        Path file = tmp.resolve("rules.json");
        storeAt(file);
        password(null);
        GovtJobsProperties props = new GovtJobsProperties();
        props.getStudent().setPasswordMin(0);
        OperatorStore store = new OperatorStore(new ObjectMapper(), new PasswordService(), props);
        String username = "min" + UUID.randomUUID().toString().substring(0, 6);
        assertThatThrownBy(() -> store.create(username, "short"))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining(String.valueOf(StudentStore.MIN_PASSWORD));
        assertThat(store.create(username, "password1234").get("role")).isEqualTo("operator");

        Files.writeString(file, "{");
        assertThatThrownBy(() -> new OperatorStore(new ObjectMapper(), new PasswordService(), props))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("could not be read");

        Path dir = tmp.resolve("not-a-file");
        Files.createDirectory(dir);
        Files.writeString(dir.resolve("ignored.json"), "{\"operators\":{}}");
        storeAt(dir);
        OperatorStore blocked = new OperatorStore(new ObjectMapper(), new PasswordService(), props);
        assertThatThrownBy(() -> blocked.create("diruser", "password1234"))
                .isInstanceOf(StoreException.class);
    }

    @Test
    void missingOperatorsKeyAndNonListAreTreatedAsEmpty() throws Exception {
        Path file = tmp.resolve("shape.json");
        Files.writeString(file, "{\"operators\":\"nope\"}");
        storeAt(file);
        password(null);
        OperatorStore store = new OperatorStore(new ObjectMapper(), new PasswordService(), new GovtJobsProperties());
        assertThat(store.operators()).isEmpty();
        assertThat(store.create("shape.user_1", "password1234").get("username")).isEqualTo("shape.user_1");
    }

    private static void storeAt(Path path) {
        System.setProperty("govtjobs.operator-store", path.toString());
    }

    private static void password(String value) {
        System.setProperty("govtjobs.operator-password", value == null ? "" : value);
    }
}
