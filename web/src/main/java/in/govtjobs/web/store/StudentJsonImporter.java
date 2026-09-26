package in.govtjobs.web.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StudentJsonImporter {

    private static final Logger log = LoggerFactory.getLogger(StudentJsonImporter.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final StudentStore students;
    private final in.govtjobs.web.config.GovtJobsProperties props;

    public StudentJsonImporter(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            StudentStore students,
            in.govtjobs.web.config.GovtJobsProperties props) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.students = students;
        this.props = props;
    }

    public void importIfEmpty() {
        if (!props.getStudent().isImportJson() || students.studentCount() > 0) {
            return;
        }
        Path file = jsonFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Map<String, Object> raw = mapper.readValue(file.toFile(), MAP);
            int n = 0;
            List<?> list = raw.get("students") instanceof List<?> l ? l : List.of();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    jdbc.update(
                            """
                            INSERT INTO students (id, email, email_norm, password_hash, created_at, last_login_at)
                            VALUES (?,?,?,?,?,?)
                            ON CONFLICT (id) DO NOTHING
                            """,
                            str(m.get("id")),
                            str(m.get("email")),
                            str(m.get("emailNorm") != null ? m.get("emailNorm") : m.get("email")),
                            str(m.get("passwordHash")),
                            ts(m.get("createdAt")),
                            ts(m.get("lastLoginAt") != null ? m.get("lastLoginAt") : m.get("createdAt")));
                    n++;
                }
            }
            if (raw.get("profiles") instanceof Map<?, ?> profiles) {
                for (Map.Entry<?, ?> e : profiles.entrySet()) {
                    jdbc.update(
                            """
                            INSERT INTO student_profiles (student_id, profile, updated_at)
                            VALUES (?, CAST(? AS jsonb), ?)
                            ON CONFLICT (student_id) DO NOTHING
                            """,
                            String.valueOf(e.getKey()),
                            mapper.writeValueAsString(e.getValue()),
                            Timestamp.from(Instant.now()));
                }
            }
            if (raw.get("items") instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> m) {
                        jdbc.update(
                                """
                                INSERT INTO desk_items
                                  (id, student_id, kind, ref_id, title, board, status, exam_date, last_date, official_url, notes, created_at, updated_at)
                                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                                ON CONFLICT (id) DO NOTHING
                                """,
                                str(m.get("id")),
                                str(m.get("studentId")),
                                str(m.get("kind")),
                                str(m.get("refId")),
                                str(m.get("title") != null ? m.get("title") : "Untitled"),
                                str(m.get("board") != null ? m.get("board") : ""),
                                str(m.get("status") != null ? m.get("status") : "watching"),
                                sqlDate(m.get("examDate")),
                                sqlDate(m.get("lastDate")),
                                str(m.get("officialUrl") != null ? m.get("officialUrl") : ""),
                                str(m.get("notes") != null ? m.get("notes") : ""),
                                ts(m.get("createdAt")),
                                ts(m.get("updatedAt")));
                    }
                }
            }
            log.info("student.json_imported path={} students={}", file, n);
        } catch (Exception e) {
            log.warn("student.json_import_skipped err={}", e.toString());
        }
    }

    private static Path jsonFile() {
        String env = System.getenv("STUDENT_DATA_DIR");
        Path dir = env == null || env.isBlank() ? RepoPaths.data().resolve("students") : Path.of(env);
        return dir.resolve("students.json");
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Timestamp ts(Object v) {
        if (v == null) {
            return Timestamp.from(Instant.now());
        }
        try {
            return Timestamp.from(Instant.parse(String.valueOf(v)));
        } catch (RuntimeException e) {
            return Timestamp.from(Instant.now());
        }
    }

    private static Date sqlDate(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        if (s.length() >= 10 && s.charAt(4) == '-') {
            try {
                return Date.valueOf(s.substring(0, 10));
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
