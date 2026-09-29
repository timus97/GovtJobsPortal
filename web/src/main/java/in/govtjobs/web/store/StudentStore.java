package in.govtjobs.web.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.desk.DeskGuidance;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.ops.OfficialUrlPolicy;
import in.govtjobs.web.security.PasswordService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class StudentStore {

    private static final Logger log = LoggerFactory.getLogger(StudentStore.class);

    public static final int MIN_PASSWORD = 10;
    public static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final ObjectMapper mapper;
    private final PasswordService passwords;
    private final JobStore jobs;
    private final GovtJobsProperties props;
    private final JdbcTemplate jdbc;
    private final Path filesDirOverride;

    @Autowired
    public StudentStore(
            ObjectMapper mapper,
            PasswordService passwords,
            JobStore jobs,
            GovtJobsProperties props,
            JdbcTemplate jdbc) {
        this(mapper, passwords, jobs, props, jdbc, null);
    }

    StudentStore(
            ObjectMapper mapper,
            PasswordService passwords,
            JobStore jobs,
            GovtJobsProperties props,
            JdbcTemplate jdbc,
            Path filesDirOverride) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.jobs = jobs;
        this.props = props;
        this.jdbc = jdbc;
        this.filesDirOverride = filesDirOverride;
    }

    public String backend() {
        return "postgres";
    }

    public long studentCount() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM students", Long.class);
        return n == null ? 0 : n;
    }

    public Map<String, Object> register(String email, String password) {
        String raw = email == null ? "" : email.trim();
        String norm = raw.toLowerCase(Locale.ROOT);
        if (raw.isBlank() || !EMAIL.matcher(norm).matches()) {
            throw new StoreException("VALIDATION", "A valid email is required");
        }
        int min = passwordMin();
        if (password == null || password.length() < min) {
            throw new StoreException("VALIDATION", "Password must be at least " + min + " characters");
        }
        String now = Instant.now().toString();
        Map<String, Object> student = new LinkedHashMap<>();
        student.put("id", UUID.randomUUID().toString());
        student.put("email", raw);
        student.put("emailNorm", norm);
        student.put("passwordHash", passwords.hash(password));
        student.put("createdAt", now);
        student.put("lastLoginAt", now);
        try {
            jdbc.update(
                    """
                    INSERT INTO students (id, email, email_norm, password_hash, created_at, last_login_at)
                    VALUES (?,?,?,?,?,?)
                    """,
                    student.get("id"),
                    raw,
                    norm,
                    student.get("passwordHash"),
                    Timestamp.from(Instant.parse(now)),
                    Timestamp.from(Instant.parse(now)));
        } catch (DuplicateKeyException e) {
            throw new StoreException("VALIDATION", "A valid email is required");
        }
        log.info("student.registered id={}", student.get("id"));
        return publicStudent(student);
    }

    public Map<String, Object> verify(String email, String password) {
        Map<String, Object> row = findInternalByEmail(email);
        if (row == null || !passwords.verify(password, String.valueOf(row.get("passwordHash")))) {
            log.info("student.login_failed");
            return null;
        }
        Instant now = Instant.now();
        jdbc.update("UPDATE students SET last_login_at = ? WHERE id = ?", Timestamp.from(now), row.get("id"));
        row.put("lastLoginAt", now.toString());
        log.info("student.login id={}", row.get("id"));
        return publicStudent(row);
    }

    public Map<String, Object> setPassword(String studentId, String password) {
        int min = passwordMin();
        if (password == null || password.length() < min) {
            throw new StoreException("VALIDATION", "Password must be at least " + min + " characters");
        }
        Map<String, Object> row = findInternalById(studentId);
        if (row == null) {
            return null;
        }
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE students SET password_hash = ?, last_login_at = ?, session_epoch = session_epoch + 1 WHERE id = ?",
                passwords.hash(password),
                Timestamp.from(now),
                studentId);
        log.info("student.password_set id={}", studentId);
        return publicStudent(row);
    }

    public long sessionEpoch(String id) {
        Map<String, Object> row = findInternalById(id);
        Object epoch = row == null ? null : row.get("sessionEpoch");
        return epoch instanceof Number n ? n.longValue() : 0L;
    }

    public Map<String, Object> findById(String id) {
        Map<String, Object> row = findInternalById(id);
        return row == null ? null : publicStudent(row);
    }

    public Map<String, Object> findInternalById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return jdbc.queryForObject("SELECT * FROM students WHERE id = ?", STUDENT, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> findInternalByEmail(String email) {
        String norm = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (norm.isEmpty()) {
            return null;
        }
        try {
            return jdbc.queryForObject("SELECT * FROM students WHERE email_norm = ?", STUDENT, norm);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> getProfile(String studentId) {
        try {
            String json = jdbc.queryForObject(
                    "SELECT profile::text FROM student_profiles WHERE student_id = ?", String.class, studentId);
            return json == null ? null : mapper.readValue(json, MAP);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IOException e) {
            throw new StoreException("IO", "Profile could not be read");
        }
    }

    public Map<String, Object> saveProfile(String studentId, Map<String, Object> profile) {
        if (findInternalById(studentId) == null) {
            return null;
        }
        Map<String, Object> next = profile == null ? new LinkedHashMap<>() : new LinkedHashMap<>(profile);
        next.put("updatedAt", Instant.now().toString());
        jdbc.update(
                """
                INSERT INTO student_profiles (student_id, profile, updated_at)
                VALUES (?, CAST(? AS jsonb), ?)
                ON CONFLICT (student_id) DO UPDATE SET profile = EXCLUDED.profile, updated_at = EXCLUDED.updated_at
                """,
                studentId,
                json(next),
                Timestamp.from(Instant.now()));
        log.info("student.profile_saved id={}", studentId);
        return next;
    }

    public Map<String, Object> listItems(String studentId, String status) {
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        String sql = "SELECT * FROM desk_items WHERE student_id = ?";
        if (status != null && !status.isBlank()) {
            if (!DeskGuidance.STATUSES.contains(status)) {
                throw new StoreException("VALIDATION", "invalid status");
            }
            sql += " AND status = ?";
            args.add(status);
        }
        List<Map<String, Object>> items = jdbc.query(sql, ITEM, args.toArray());
        Map<String, List<Map<String, Object>>> filesByItem = filesForStudent(studentId);
        List<Map<String, Object>> decorated = new ArrayList<>();
        for (Map<String, Object> item : items) {
            decorated.add(decorate(item, filesByItem.getOrDefault(String.valueOf(item.get("id")), List.of())));
        }
        decorated.sort(Comparator.comparingInt(StudentStore::sortKey));
        Long mocks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mock_attempts WHERE student_id = ? AND submitted_at IS NOT NULL",
                Long.class,
                studentId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put(
                "upcoming",
                decorated.stream()
                        .filter(i -> i.get("daysLeft") instanceof Number n && n.intValue() >= 0)
                        .count());
        stats.put(
                "admitPending",
                decorated.stream()
                        .filter(i -> "applied".equals(i.get("status")) && !Boolean.TRUE.equals(i.get("hasAdmit")))
                        .count());
        Object nearest = decorated.stream()
                .map(i -> i.get("daysLeft"))
                .filter(Number.class::isInstance)
                .map(n -> ((Number) n).intValue())
                .filter(n -> n >= 0)
                .min(Integer::compareTo)
                .orElse(null);
        stats.put("nearestDays", nearest);
        stats.put("mocksCompleted", mocks == null ? 0 : mocks);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", decorated);
        out.put("total", decorated.size());
        out.put("stats", stats);
        return out;
    }

    public Map<String, Object> getItem(String studentId, String id) {
        try {
            Map<String, Object> item = jdbc.queryForObject(
                    "SELECT * FROM desk_items WHERE id = ? AND student_id = ?", ITEM, id, studentId);
            return decorate(item, filesForItem(id));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> createItem(String studentId, Map<String, Object> body) {
        if (findInternalById(studentId) == null) {
            throw new StoreException("AUTH", "Not signed in");
        }
        String kind = String.valueOf(body.getOrDefault("kind", "custom"));
        if (!DeskGuidance.KINDS.contains(kind)) {
            throw new StoreException("VALIDATION", "kind must be series, opportunity, or custom");
        }
        String status = body.get("status") == null ? "watching" : String.valueOf(body.get("status"));
        if (!DeskGuidance.STATUSES.contains(status)) {
            throw new StoreException("VALIDATION", "invalid status");
        }
        String refId = body.get("refId") == null ? null : String.valueOf(body.get("refId")).trim();
        if (refId != null && refId.isBlank()) {
            refId = null;
        }
        Map<String, Object> catalog = null;
        if ("series".equals(kind) || "opportunity".equals(kind)) {
            if (refId == null) {
                throw new StoreException("VALIDATION", "refId is required");
            }
            catalog = catalogFor(kind, refId);
            if (catalog == null) {
                throw new StoreException(
                        "VALIDATION", "series".equals(kind) ? "Exam series not found" : "Job not found");
            }
            Map<String, Object> existing = findByRef(studentId, kind, refId);
            if (existing != null) {
                if (!status.equals(existing.get("status"))) {
                    jdbc.update(
                            "UPDATE desk_items SET status = ?, updated_at = ? WHERE id = ?",
                            status,
                            Timestamp.from(Instant.now()),
                            existing.get("id"));
                    existing.put("status", status);
                }
                return decorate(existing, filesForItem(String.valueOf(existing.get("id"))));
            }
        }
        String title = trimToNull(body.get("title"));
        if (title == null && catalog != null) {
            title = String.valueOf(catalog.getOrDefault("title", "Untitled"));
        }
        if ("custom".equals(kind) && (title == null || title.isBlank())) {
            throw new StoreException("VALIDATION", "title is required");
        }
        if (title == null || title.isBlank()) {
            title = "Untitled";
        }
        String examDate = DeskGuidance.formatIsoDate(body.get("examDate"));
        String lastDate = DeskGuidance.formatIsoDate(body.get("lastDate"));
        if ("custom".equals(kind) && examDate == null && lastDate == null) {
            throw new StoreException("VALIDATION", "custom exam needs an exam date or last date");
        }
        requireHttpsIfPresent(body.get("officialUrl"));
        String officialUrl = body.get("officialUrl") == null ? "" : String.valueOf(body.get("officialUrl")).trim();
        if ("null".equals(officialUrl)) {
            officialUrl = "";
        }
        String board = body.get("board") == null ? "" : String.valueOf(body.get("board")).trim();
        if (catalog != null && board.isBlank() && catalog.get("board") != null) {
            board = String.valueOf(catalog.get("board"));
        }
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO desk_items
                  (id, student_id, kind, ref_id, title, board, status, exam_date, last_date, official_url, notes, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                studentId,
                kind,
                refId,
                title,
                board,
                status,
                sqlDate(examDate),
                sqlDate(lastDate),
                officialUrl,
                body.get("notes") == null ? "" : String.valueOf(body.get("notes")).trim(),
                Timestamp.from(now),
                Timestamp.from(now));
        log.info("desk.item_created id={} kind={} student={}", id, kind, studentId);
        return getItem(studentId, id);
    }

    public Map<String, Object> patchItem(String studentId, String id, Map<String, Object> patch) {
        Map<String, Object> item = rawItem(studentId, id);
        if (item == null) {
            return null;
        }
        if (patch != null && patch.containsKey("officialUrl")) {
            requireHttpsIfPresent(patch.get("officialUrl"));
        }
        if (patch != null && patch.containsKey("status") && patch.get("status") != null) {
            String status = String.valueOf(patch.get("status"));
            if (!DeskGuidance.STATUSES.contains(status)) {
                throw new StoreException("VALIDATION", "invalid status");
            }
            item.put("status", status);
        }
        if (patch != null) {
            if (patch.containsKey("examDate")) {
                item.put("examDate", DeskGuidance.formatIsoDate(patch.get("examDate")));
            }
            if (patch.containsKey("lastDate")) {
                item.put("lastDate", DeskGuidance.formatIsoDate(patch.get("lastDate")));
            }
            if (patch.containsKey("officialUrl")) {
                String url = patch.get("officialUrl") == null ? "" : String.valueOf(patch.get("officialUrl")).trim();
                item.put("officialUrl", url);
            }
            if (patch.containsKey("notes")) {
                item.put("notes", patch.get("notes") == null ? "" : String.valueOf(patch.get("notes")));
            }
            if (patch.containsKey("title") && "custom".equals(item.get("kind"))) {
                String title = patch.get("title") == null ? "" : String.valueOf(patch.get("title")).trim();
                if (title.isBlank()) {
                    throw new StoreException("VALIDATION", "title is required");
                }
                item.put("title", title);
            }
            if (patch.containsKey("board") && "custom".equals(item.get("kind"))) {
                item.put("board", patch.get("board") == null ? "" : String.valueOf(patch.get("board")).trim());
            }
        }
        jdbc.update(
                """
                UPDATE desk_items SET
                  status = ?, exam_date = ?, last_date = ?, official_url = ?, notes = ?,
                  title = ?, board = ?, updated_at = ?
                WHERE id = ? AND student_id = ?
                """,
                item.get("status"),
                sqlDate(item.get("examDate")),
                sqlDate(item.get("lastDate")),
                item.get("officialUrl") == null ? "" : String.valueOf(item.get("officialUrl")),
                item.get("notes") == null ? "" : String.valueOf(item.get("notes")),
                item.get("title"),
                item.get("board") == null ? "" : String.valueOf(item.get("board")),
                Timestamp.from(Instant.now()),
                id,
                studentId);
        return getItem(studentId, id);
    }

    public boolean deleteItem(String studentId, String id) {
        int n = jdbc.update("DELETE FROM desk_items WHERE id = ? AND student_id = ?", id, studentId);
        if (n > 0) {
            deleteTree(StudentFilePaths.itemDir(filesDir(), studentId, id));
        }
        return n > 0;
    }

    public Map<String, Object> saveFile(String studentId, String itemId, String kind, String originalName, byte[] bytes)
            throws IOException {
        if (getItem(studentId, itemId) == null) {
            throw new StoreException("NOT_FOUND", "Desk item not found");
        }
        if (!List.of("admit", "result").contains(kind)) {
            throw new StoreException("VALIDATION", "kind must be admit or result");
        }
        long max = maxFileBytes();
        if (bytes == null || bytes.length == 0 || bytes.length > max) {
            throw new StoreException("VALIDATION", "File must be a PDF, JPEG, or PNG up to 5 MB");
        }
        String mime = detectMime(bytes);
        if (mime == null) {
            throw new StoreException("VALIDATION", "Only PDF, JPEG, and PNG are allowed");
        }
        String ext = mime.contains("pdf") ? "pdf" : mime.contains("png") ? "png" : "jpg";
        String stored = StudentFilePaths.storedPath(studentId, itemId, kind, ext);
        Path dest = StudentFilePaths.resolveUnder(filesDir(), stored);
        if (stored == null || dest == null || !StudentFilePaths.isSafeStoredPath(studentId, itemId, stored)) {
            throw new StoreException("VALIDATION", "Invalid file path");
        }
        Files.createDirectories(dest.getParent());
        unlinkKindFiles(studentId, itemId, kind);
        Files.write(dest, bytes);
        StudentFilePaths.restrictOwnerReadWrite(dest);
        Instant now = Instant.now();
        String fileId = UUID.randomUUID().toString();
        String name = sanitizeName(originalName, kind, ext);
        jdbc.update(
                """
                INSERT INTO desk_files (id, item_id, kind, stored_path, mime, bytes, original_name, uploaded_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (item_id, kind) DO UPDATE SET
                  id = EXCLUDED.id, stored_path = EXCLUDED.stored_path, mime = EXCLUDED.mime,
                  bytes = EXCLUDED.bytes, original_name = EXCLUDED.original_name, uploaded_at = EXCLUDED.uploaded_at
                """,
                fileId,
                itemId,
                kind,
                stored,
                mime,
                bytes.length,
                name,
                Timestamp.from(now));
        jdbc.update("UPDATE desk_items SET updated_at = ? WHERE id = ?", Timestamp.from(now), itemId);
        log.info("desk.file_saved item={} kind={} bytes={}", itemId, kind, bytes.length);
        return getItem(studentId, itemId);
    }

    public Path resolveFile(String studentId, String itemId, String kind) {
        if (getItem(studentId, itemId) == null) {
            return null;
        }
        List<Map<String, Object>> files = filesForItem(itemId);
        return files.stream()
                .filter(f -> kind.equals(f.get("kind")))
                .findFirst()
                .map(f -> safeFile(studentId, itemId, String.valueOf(f.get("storedPath"))))
                .orElse(null);
    }

    public Map<String, Object> fileMeta(String studentId, String itemId, String kind) {
        if (getItem(studentId, itemId) == null) {
            return null;
        }
        return filesForItem(itemId).stream().filter(f -> kind.equals(f.get("kind"))).findFirst().orElse(null);
    }

    public Map<String, Object> deleteFile(String studentId, String itemId, String kind) {
        if (getItem(studentId, itemId) == null) {
            return null;
        }
        int n = jdbc.update("DELETE FROM desk_files WHERE item_id = ? AND kind = ?", itemId, kind);
        unlinkKindFiles(studentId, itemId, kind);
        if (n > 0) {
            jdbc.update("UPDATE desk_items SET updated_at = ? WHERE id = ?", Timestamp.from(Instant.now()), itemId);
        }
        return getItem(studentId, itemId);
    }

    public void setTopicDone(String studentId, String seriesId, String topicId, boolean done) {
        if (done) {
            jdbc.update(
                    """
                    INSERT INTO topic_progress (student_id, series_id, topic_id, done_at)
                    VALUES (?,?,?,?)
                    ON CONFLICT (student_id, series_id, topic_id) DO UPDATE SET done_at = EXCLUDED.done_at
                    """,
                    studentId,
                    seriesId,
                    topicId,
                    Timestamp.from(Instant.now()));
        } else {
            jdbc.update(
                    "DELETE FROM topic_progress WHERE student_id = ? AND series_id = ? AND topic_id = ?",
                    studentId,
                    seriesId,
                    topicId);
        }
    }

    public Map<String, String> topicProgress(String studentId, String seriesId) {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.query(
                "SELECT topic_id, done_at FROM topic_progress WHERE student_id = ? AND series_id = ?",
                rs -> {
                    Timestamp ts = rs.getTimestamp("done_at");
                    out.put(rs.getString("topic_id"), ts == null ? "" : ts.toInstant().toString());
                },
                studentId,
                seriesId);
        return out;
    }

    public Map<String, Object> openAttempt(String studentId, String seriesId, String itemId) {
        List<Map<String, Object>> open = jdbc.query(
                """
                SELECT * FROM mock_attempts
                WHERE student_id = ? AND series_id = ? AND submitted_at IS NULL
                ORDER BY started_at DESC LIMIT 1
                """,
                attemptMapper(),
                studentId,
                seriesId);
        if (!open.isEmpty()) {
            return open.get(0);
        }
        Instant now = Instant.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("studentId", studentId);
        row.put("seriesId", seriesId);
        row.put("itemId", itemId);
        row.put("startedAt", now.toString());
        row.put("submittedAt", null);
        row.put("answers", new LinkedHashMap<>());
        jdbc.update(
                """
                INSERT INTO mock_attempts
                  (id, student_id, series_id, item_id, started_at, submitted_at, score, total, answers)
                VALUES (?,?,?,?,?,?,?,?, CAST(? AS jsonb))
                """,
                row.get("id"),
                studentId,
                seriesId,
                itemId,
                Timestamp.from(now),
                null,
                null,
                null,
                "{}");
        return row;
    }

    public Map<String, Object> getAttempt(String studentId, String attemptId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM mock_attempts WHERE id = ? AND student_id = ?",
                    attemptMapper(),
                    attemptId,
                    studentId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> submitAttempt(
            String studentId, String attemptId, Map<String, Object> answers, int score, int total) {
        Map<String, Object> row = getAttempt(studentId, attemptId);
        if (row == null) {
            log.warn("mock.submit_missing id={}", attemptId);
            return null;
        }
        if (row.get("submittedAt") != null) {
            throw new StoreException("CONFLICT", "Already submitted");
        }
        Instant now = Instant.now();
        jdbc.update(
                """
                UPDATE mock_attempts SET submitted_at = ?, score = ?, total = ?, answers = CAST(? AS jsonb)
                WHERE id = ? AND student_id = ?
                """,
                Timestamp.from(now),
                score,
                total,
                json(answers == null ? Map.of() : answers),
                attemptId,
                studentId);
        row.put("submittedAt", now.toString());
        row.put("answers", answers);
        row.put("score", score);
        row.put("total", total);
        log.info("mock.submitted id={} score={}/{}", attemptId, score, total);
        return row;
    }

    public Path filesDir() {
        if (filesDirOverride != null) {
            return filesDirOverride;
        }
        String configured = props == null ? null : props.getStudent().getFilesDir();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        String env = System.getenv("STUDENT_FILES_DIR");
        return env == null || env.isBlank()
                ? in.govtjobs.domain.RepoPaths.data().resolve("student-files")
                : Path.of(env);
    }

    private Map<String, Object> findByRef(String studentId, String kind, String refId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT * FROM desk_items WHERE student_id = ? AND kind = ? AND ref_id = ?",
                ITEM,
                studentId,
                kind,
                refId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> rawItem(String studentId, String id) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM desk_items WHERE id = ? AND student_id = ?", ITEM, id, studentId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private List<Map<String, Object>> filesForItem(String itemId) {
        return jdbc.query("SELECT * FROM desk_files WHERE item_id = ?", FILE, itemId);
    }

    private Map<String, List<Map<String, Object>>> filesForStudent(String studentId) {
        List<Map<String, Object>> rows = jdbc.query(
                """
                SELECT f.* FROM desk_files f
                JOIN desk_items i ON i.id = f.item_id
                WHERE i.student_id = ?
                """,
                FILE,
                studentId);
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            out.computeIfAbsent(String.valueOf(row.get("itemId")), k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    private Map<String, Object> decorate(Map<String, Object> row, List<Map<String, Object>> files) {
        Map<String, Object> catalog = catalogFor(
                String.valueOf(row.get("kind")), row.get("refId") == null ? null : String.valueOf(row.get("refId")));
        Map<String, Object> merged = new LinkedHashMap<>(row);
        if (catalog != null) {
            if (merged.get("title") == null) merged.put("title", catalog.get("title"));
            if (merged.get("board") == null || String.valueOf(merged.get("board")).isBlank()) {
                merged.put("board", catalog.get("board"));
            }
            if (merged.get("officialUrl") == null || String.valueOf(merged.get("officialUrl")).isBlank()) {
                merged.put("officialUrl", catalog.get("officialUrl"));
            }
            if (merged.get("examDate") == null) merged.put("examDate", catalog.get("examDate"));
            if (merged.get("lastDate") == null) merged.put("lastDate", catalog.get("lastDate"));
            merged.put("applyOpen", catalog.get("applyOpen"));
        }
        boolean hasAdmit = files.stream().anyMatch(f -> "admit".equals(f.get("kind")));
        boolean hasResult = files.stream().anyMatch(f -> "result".equals(f.get("kind")));
        merged.put("hasAdmit", hasAdmit);
        merged.put("hasResult", hasResult);
        return DeskGuidance.decorateItem(merged);
    }

    private Map<String, Object> catalogFor(String kind, String refId) {
        if (refId == null) return null;
        if ("series".equals(kind)) {
            Map<String, Object> series = jobs.getExamSeriesById(refId);
            if (series == null) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("title", series.get("name"));
            out.put("board", series.get("board"));
            out.put("officialUrl", series.get("officialUrl"));
            out.put("examDate", series.get("expectedExam"));
            out.put("lastDate", series.get("expectedApply"));
            out.put("applyOpen", Boolean.TRUE.equals(series.get("canApply")));
            return out;
        }
        if ("opportunity".equals(kind)) {
            Map<String, Object> job = jobs.getJobById(refId);
            if (job == null) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("title", job.get("title"));
            out.put("board", job.get("organization"));
            out.put("officialUrl", job.get("officialUrl"));
            out.put("examDate", job.get("examDate"));
            out.put("lastDate", job.get("lastDate"));
            String status = String.valueOf(job.get("status"));
            out.put("applyOpen", "open".equals(status) || "closing_soon".equals(status));
            return out;
        }
        return null;
    }

    private static String detectMime(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46) {
            return "application/pdf";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47) {
            return "image/png";
        }
        return null;
    }

    private static void requireHttpsIfPresent(Object raw) {
        if (raw == null) {
            return;
        }
        String url = String.valueOf(raw).trim();
        if (url.isEmpty() || "null".equals(url)) {
            return;
        }
        if (!OfficialUrlPolicy.isHttps(url)) {
            throw new StoreException("VALIDATION", "officialUrl must be https");
        }
    }

    private Path safeFile(String studentId, String itemId, String storedPath) {
        if (!StudentFilePaths.isSafeStoredPath(studentId, itemId, storedPath)) {
            return null;
        }
        Path resolved = StudentFilePaths.resolveUnder(filesDir(), storedPath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    private void unlinkKindFiles(String studentId, String itemId, String kind) {
        Path dir = StudentFilePaths.itemDir(filesDir(), studentId, itemId);
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        for (String ext : List.of("pdf", "jpg", "png")) {
            try {
                Files.deleteIfExists(dir.resolve(kind + "." + ext));
            } catch (IOException ignored) {
                // best-effort unlink of the replaced kind
            }
        }
    }

    private static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort disk cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort disk cleanup
        }
    }

    private int passwordMin() {
        int n = props == null ? MIN_PASSWORD : props.getStudent().getPasswordMin();
        return n > 0 ? n : MIN_PASSWORD;
    }

    private long maxFileBytes() {
        long n = props == null ? MAX_FILE_BYTES : props.getStudent().getMaxFileBytes();
        return n > 0 ? n : MAX_FILE_BYTES;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new StoreException("IO", "Could not write JSON");
        }
    }

    private static Date sqlDate(Object value) {
        String iso = DeskGuidance.formatIsoDate(value);
        return iso == null ? null : Date.valueOf(iso);
    }

    private static String isoTs(Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }

    private static String isoDate(Date date) {
        return date == null ? null : date.toLocalDate().toString();
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static String sanitizeName(String originalName, String kind, String ext) {
        String fallback = ("admit".equals(kind) ? "admit-card." : "result.") + ext;
        if (originalName == null || originalName.isBlank()) {
            return fallback;
        }
        String base = originalName.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^A-Za-z0-9._\\- ()\\[\\]]+", "_").replaceAll("^\\.+", "");
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        if (base.matches("(?i).+\\.(pdf|png|jpe?g)")) {
            return base.replace("\"", "");
        }
        return fallback;
    }

    private static int sortKey(Map<String, Object> item) {
        Object days = item.get("daysLeft");
        if (!(days instanceof Number n)) {
            return 99_999;
        }
        int v = n.intValue();
        return v < 0 ? 50_000 - v : v;
    }

    private static Map<String, Object> publicStudent(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("email", row.get("email"));
        out.put("createdAt", row.get("createdAt"));
        return out;
    }

    private static final RowMapper<Map<String, Object>> STUDENT = (rs, i) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("email", rs.getString("email"));
        row.put("emailNorm", rs.getString("email_norm"));
        row.put("passwordHash", rs.getString("password_hash"));
        row.put("createdAt", isoTs(rs.getTimestamp("created_at")));
        row.put("lastLoginAt", isoTs(rs.getTimestamp("last_login_at")));
        row.put("sessionEpoch", rs.getLong("session_epoch"));
        return row;
    };

    private static final RowMapper<Map<String, Object>> ITEM = (rs, i) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("studentId", rs.getString("student_id"));
        row.put("kind", rs.getString("kind"));
        row.put("refId", rs.getString("ref_id"));
        row.put("title", rs.getString("title"));
        row.put("board", rs.getString("board"));
        row.put("status", rs.getString("status"));
        row.put("examDate", isoDate(rs.getDate("exam_date")));
        row.put("lastDate", isoDate(rs.getDate("last_date")));
        row.put("officialUrl", rs.getString("official_url"));
        row.put("notes", rs.getString("notes"));
        row.put("createdAt", isoTs(rs.getTimestamp("created_at")));
        row.put("updatedAt", isoTs(rs.getTimestamp("updated_at")));
        return row;
    };

    private static final RowMapper<Map<String, Object>> FILE = (rs, i) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("itemId", rs.getString("item_id"));
        row.put("kind", rs.getString("kind"));
        row.put("storedPath", rs.getString("stored_path"));
        row.put("mime", rs.getString("mime"));
        row.put("bytes", rs.getInt("bytes"));
        row.put("originalName", rs.getString("original_name"));
        row.put("uploadedAt", isoTs(rs.getTimestamp("uploaded_at")));
        return row;
    };

    private RowMapper<Map<String, Object>> attemptMapper() {
        return (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("studentId", rs.getString("student_id"));
            row.put("seriesId", rs.getString("series_id"));
            row.put("itemId", rs.getString("item_id"));
            row.put("startedAt", isoTs(rs.getTimestamp("started_at")));
            row.put("submittedAt", isoTs(rs.getTimestamp("submitted_at")));
            row.put("score", rs.getObject("score"));
            row.put("total", rs.getObject("total"));
            String answers = rs.getString("answers");
            try {
                row.put("answers", answers == null ? Map.of() : mapper.readValue(answers, MAP));
            } catch (IOException e) {
                row.put("answers", Map.of());
            }
            return row;
        };
    }
}
