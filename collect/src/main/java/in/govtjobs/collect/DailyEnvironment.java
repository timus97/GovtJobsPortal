package in.govtjobs.collect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Process environment, with {@code .env} filling only keys that are not already set. */
final class DailyEnvironment {

    private DailyEnvironment() {}

    static Map<String, String> load(Path root) throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
        Path file = root.resolve(".env");
        if (Files.isRegularFile(file)) {
            for (String line : Files.readAllLines(file)) {
                if (line == null || line.isBlank() || line.trim().startsWith("#")) {
                    continue;
                }
                int split = line.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                String key = line.substring(0, split).trim();
                String value = line.substring(split + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                        || (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isBlank()) {
                    env.putIfAbsent(key, value);
                }
            }
        }
        env.putAll(System.getenv());
        return env;
    }
}
