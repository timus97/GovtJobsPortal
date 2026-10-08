package in.govtjobs.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator approval of one quarantine row. The next {@code process} applies it.
 * Approval does not write jobs.json by itself.
 */
public final class QuarantineApproval {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    private QuarantineApproval() {}

    public static void approve(Path root, String id, String reason) throws IOException {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason required");
        }
        Path quarantine = root.resolve("data").resolve("processed").resolve("quarantine.json");
        if (!contains(quarantine, id)) {
            throw new IllegalArgumentException("Quarantine row not found: " + id);
        }
        Path file = root.resolve("data").resolve("seed").resolve("approvals.json");
        Map<String, Object> document = read(file);
        List<Map<String, Object>> approvals = new ArrayList<>();
        if (document.get("approvals") instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map && !id.equals(String.valueOf(map.get("id")))) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    approvals.add(copy);
                }
            }
        }
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("id", id);
        approval.put("reason", reason.trim());
        approvals.add(approval);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("approvals", approvals);
        BuildJobs.writeAtomic(file, out);
    }

    static Map<String, String> load(Path file) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, Object> document = readQuiet(file);
        if (!(document.get("approvals") instanceof List<?> rows)) {
            return out;
        }
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map && map.get("id") != null) {
                out.put(String.valueOf(map.get("id")), map.get("reason") == null ? "" : String.valueOf(map.get("reason")));
            }
        }
        return out;
    }

    private static boolean contains(Path file, String id) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        JsonNode node = MAPPER.readTree(file.toFile());
        if (node == null || !node.isArray()) {
            return false;
        }
        List<Map<String, Object>> rows = MAPPER.convertValue(node, LIST);
        if (rows == null) {
            return false;
        }
        for (Map<String, Object> row : rows) {
            Object job = row.get("job");
            if (job instanceof Map<?, ?> map && id.equals(String.valueOf(map.get("id")))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> read(Path file) throws IOException {
        return readQuiet(file);
    }

    private static Map<String, Object> readQuiet(Path file) {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = MAPPER.readTree(file.toFile());
            if (node == null || !node.isObject()) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> value = MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {});
            return value == null ? new LinkedHashMap<>() : value;
        } catch (IOException ex) {
            return new LinkedHashMap<>();
        }
    }
}
