package in.govtjobs.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class JsonFilesTest {

    @TempDir
    Path tmp;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void missingFileUsesFallbackCorruptFileThrows() throws Exception {
        Path missing = tmp.resolve("gone.json");
        Map<String, Object> fallback = Map.of("ok", true);
        Map<String, Object> read = JsonFiles.read(
                missing, mapper, new TypeReference<>() {}, fallback, LoggerFactory.getLogger(JsonFilesTest.class));
        assertThat(read).isEqualTo(fallback);

        Path corrupt = tmp.resolve("bad.json");
        Files.writeString(corrupt, "{not-json");
        assertThatThrownBy(() -> JsonFiles.read(
                        corrupt, mapper, new TypeReference<Map<String, Object>>() {}, fallback, LoggerFactory.getLogger(JsonFilesTest.class)))
                .isInstanceOf(IllegalStateException.class);
    }
}
