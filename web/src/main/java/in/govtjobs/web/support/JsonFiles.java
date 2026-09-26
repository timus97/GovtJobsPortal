package in.govtjobs.web.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;

/** Atomic JSON read/write with logging. Callers never swallow I/O silently. */
public final class JsonFiles {

    private JsonFiles() {}

    /**
     * Missing file returns {@code fallback}. Unreadable or corrupt JSON throws so
     * callers do not replace a store with an empty document.
     */
    public static <T> T read(Path path, ObjectMapper mapper, TypeReference<T> type, T fallback, Logger log) {
        if (!Files.isRegularFile(path)) {
            if (log.isDebugEnabled()) {
                log.debug("json.missing path={}", path);
            }
            return fallback;
        }
        try {
            T value = mapper.readValue(path.toFile(), type);
            return value == null ? fallback : value;
        } catch (IOException e) {
            log.error("json.read_failed path={} err={}", path, e.toString());
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    public static void writeAtomic(Path path, Object value, ObjectMapper mapper, Logger log) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("json.write_failed path={} err={}", path, e.toString());
            throw new IllegalStateException("Failed to write " + path, e);
        }
    }

    public static long mtime(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        } catch (IOException e) {
            return -1L;
        }
    }
}
