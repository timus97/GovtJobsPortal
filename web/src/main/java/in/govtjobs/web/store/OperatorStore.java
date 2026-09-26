package in.govtjobs.web.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.security.PasswordService;
import in.govtjobs.web.support.JsonFiles;
import in.govtjobs.web.support.JsonMaps;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OperatorStore {

    private static final Logger log = LoggerFactory.getLogger(OperatorStore.class);

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private final ObjectMapper mapper;
    private final PasswordService passwords;
    private final GovtJobsProperties props;
    private final Object lock = new Object();

    public OperatorStore(ObjectMapper mapper, PasswordService passwords, GovtJobsProperties props) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.props = props;
        bootstrapIfEmpty();
    }

    public Map<String, Object> findByUsername(String username) {
        if (username == null) return null;
        return operators().stream()
                .filter(o -> username.equalsIgnoreCase(String.valueOf(o.get("username"))))
                .findFirst()
                .orElse(null);
    }

    public boolean verify(String username, String password) {
        Map<String, Object> row = findByUsername(username);
        return row != null && passwords.verify(password, String.valueOf(row.get("passwordHash")));
    }

    public Map<String, Object> create(String username, String password) {
        if (username == null || !username.matches("^[A-Za-z0-9._-]{3,32}$")) {
            throw new StoreException("VALIDATION", "Username must be 3–32 letters, digits, dot, underscore or hyphen");
        }
        int min = passwordMin();
        if (password == null || password.length() < min) {
            throw new StoreException("VALIDATION", "Password must be at least " + min + " characters");
        }
        synchronized (lock) {
            Map<String, Object> data = load();
            List<Map<String, Object>> ops = operatorsOf(data);
            if (ops.stream().anyMatch(o -> username.equalsIgnoreCase(String.valueOf(o.get("username"))))) {
                throw new StoreException("DUPLICATE", "Username already exists");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", UUID.randomUUID().toString());
            row.put("username", username);
            row.put("passwordHash", passwords.hash(password));
            row.put("role", "operator");
            row.put("createdAt", Instant.now().toString());
            ops.add(row);
            data.put("operators", ops);
            save(data);
            log.info("ops.operator_created username={}", username);
            Map<String, Object> pub = new LinkedHashMap<>();
            pub.put("id", row.get("id"));
            pub.put("username", username);
            pub.put("role", "operator");
            pub.put("createdAt", row.get("createdAt"));
            return pub;
        }
    }

    public List<Map<String, Object>> operators() {
        return operatorsOf(load());
    }

    private void bootstrapIfEmpty() {
        synchronized (lock) {
            Map<String, Object> data = load();
            if (!operatorsOf(data).isEmpty()) return;
            String pwd = System.getenv("OPERATOR_PASSWORD");
            if (pwd == null || pwd.isBlank()) return;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", UUID.randomUUID().toString());
            row.put("username", "admin");
            row.put("passwordHash", passwords.hash(pwd));
            row.put("role", "admin");
            row.put("createdAt", Instant.now().toString());
            List<Map<String, Object>> ops = operatorsOf(data);
            ops.add(row);
            data.put("operators", ops);
            save(data);
            log.info("ops.bootstrap_admin created=true");
        }
    }

    private Path path() {
        String env = System.getenv("OPERATOR_STORE_PATH");
        return env == null || env.isBlank()
                ? RepoPaths.data().resolve("ops").resolve("operators.json")
                : Path.of(env);
    }

    private int passwordMin() {
        int n = props == null ? StudentStore.MIN_PASSWORD : props.getStudent().getPasswordMin();
        return n > 0 ? n : StudentStore.MIN_PASSWORD;
    }

    private Map<String, Object> load() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("operators", new ArrayList<>());
        try {
            Map<String, Object> raw = JsonFiles.read(path(), mapper, MAP, empty, log);
            if (raw == null) {
                return empty;
            }
            raw.putIfAbsent("operators", new ArrayList<>());
            return raw;
        } catch (IllegalStateException e) {
            throw new StoreException("IO", "Operator store could not be read");
        }
    }

    private void save(Map<String, Object> data) {
        try {
            JsonFiles.writeAtomic(path(), data, mapper, log);
        } catch (IllegalStateException e) {
            throw new StoreException("IO", e.getMessage());
        }
    }

    private List<Map<String, Object>> operatorsOf(Map<String, Object> data) {
        return JsonMaps.listOf(data, "operators");
    }
}
