package in.govtjobs.web.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.security.PasswordService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StudentJsonImporterTest {

    @TempDir
    Path tmp;

    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private StudentStore students;
    private StudentJsonImporter importer;
    private GovtJobsProperties props;
    private String schema;
    private String previousDataDir;

    @BeforeEach
    void setUp() {
        previousDataDir = System.getenv("STUDENT_DATA_DIR");
        schema = "cov_imp_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource adminDs = dataSource("jdbc:postgresql://127.0.0.1:5432/govtjobs_students");
        admin = new JdbcTemplate(adminDs);
        admin.execute("CREATE SCHEMA " + schema);
        admin.execute(
                """
                CREATE TABLE %s.students (
                  id TEXT PRIMARY KEY,
                  email TEXT,
                  email_norm TEXT,
                  password_hash TEXT,
                  created_at TIMESTAMPTZ,
                  last_login_at TIMESTAMPTZ,
                  session_epoch BIGINT NOT NULL DEFAULT 0
                )
                """
                        .formatted(schema));
        admin.execute(
                """
                CREATE TABLE %s.student_profiles (
                  student_id TEXT PRIMARY KEY,
                  profile JSONB NOT NULL,
                  updated_at TIMESTAMPTZ NOT NULL
                )
                """
                        .formatted(schema));
        admin.execute(
                """
                CREATE TABLE %s.desk_items (
                  id TEXT PRIMARY KEY,
                  student_id TEXT,
                  kind TEXT,
                  ref_id TEXT,
                  title TEXT,
                  board TEXT,
                  status TEXT,
                  exam_date DATE,
                  last_date DATE,
                  official_url TEXT,
                  notes TEXT,
                  created_at TIMESTAMPTZ,
                  updated_at TIMESTAMPTZ
                )
                """
                        .formatted(schema));
        DriverManagerDataSource ds = dataSource(
                "jdbc:postgresql://127.0.0.1:5432/govtjobs_students?currentSchema=" + schema);
        jdbc = new JdbcTemplate(ds);
        props = new GovtJobsProperties();
        props.getStudent().setImportJson(false);
        ObjectMapper mapper = new ObjectMapper();
        students = new StudentStore(
                mapper, new PasswordService(), new JobStore(mapper), props, jdbc, tmp.resolve("files"));
        importer = new StudentJsonImporter(jdbc, mapper, students, props);
        TestProcessEnv.set("STUDENT_DATA_DIR", tmp.toString());
    }

    @AfterEach
    void tearDown() {
        TestProcessEnv.set("STUDENT_DATA_DIR", previousDataDir);
        admin.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    void skipsWhenImportDisabledOrFileMissingOrStudentsAlreadyPresent() throws Exception {
        props.getStudent().setImportJson(false);
        writeStudents(Map.of("students", List.of(student("skip-1", "skip@example.com"))));
        importer.importIfEmpty();
        assertThat(students.studentCount()).isZero();

        props.getStudent().setImportJson(true);
        Files.delete(tmp.resolve("students.json"));
        importer.importIfEmpty();
        assertThat(students.studentCount()).isZero();

        writeStudents(Map.of("students", List.of(student("present-1", "present@example.com"))));
        importer.importIfEmpty();
        assertThat(students.studentCount()).isEqualTo(1);
        writeStudents(Map.of("students", List.of(
                student("present-1", "present@example.com"),
                student("present-2", "second@example.com"))));
        importer.importIfEmpty();
        assertThat(students.studentCount()).isEqualTo(1);
    }

    @Test
    void emptyFileImportsNothing() throws Exception {
        props.getStudent().setImportJson(true);
        writeStudents(Map.of());
        importer.importIfEmpty();
        assertThat(students.studentCount()).isZero();
        writeStudents(Map.of("students", List.of(), "profiles", "nope", "items", List.of("skip")));
        importer.importIfEmpty();
        assertThat(students.studentCount()).isZero();
    }

    @Test
    void importsOneStudentProfileAndItemAndIgnoresDuplicateId() throws Exception {
        props.getStudent().setImportJson(true);
        String id = "imp-" + UUID.randomUUID();
        Map<String, Object> row = student(id, "Imported@example.com");
        row.put("emailNorm", "imported@example.com");
        row.put("createdAt", "not-a-timestamp");
        row.put("lastLoginAt", "2020-01-01T00:00:00Z");
        Map<String, Object> again = student(id, "Imported@example.com");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", "Imported");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "item-" + id);
        item.put("studentId", id);
        item.put("kind", "custom");
        item.put("title", null);
        item.put("examDate", "not-a-date");
        item.put("lastDate", "2027-05-01T00:00:00Z");
        item.put("officialUrl", null);
        item.put("createdAt", null);
        item.put("updatedAt", "2024-01-02T03:04:05Z");
        Map<String, Object> badDate = new LinkedHashMap<>();
        badDate.put("id", "bad-" + id);
        badDate.put("studentId", id);
        badDate.put("kind", "custom");
        badDate.put("title", "Dated");
        badDate.put("examDate", "2026-99-99");
        badDate.put("lastDate", "short");
        writeStudents(Map.of(
                "students", List.of("skip-me", row, again),
                "profiles", Map.of(id, profile),
                "items", List.of(item, badDate)));
        importer.importIfEmpty();

        assertThat(students.studentCount()).isEqualTo(1);
        Map<String, Object> stored = students.findInternalById(id);
        assertThat(stored.get("email")).isEqualTo("Imported@example.com");
        assertThat(stored.get("emailNorm")).isEqualTo("imported@example.com");
        assertThat(students.getProfile(id)).containsEntry("name", "Imported");
        Integer items = jdbc.queryForObject("SELECT COUNT(*) FROM desk_items", Integer.class);
        assertThat(items).isEqualTo(2);
        Map<String, Object> itemRow = jdbc.queryForMap(
                "SELECT title, status, exam_date FROM desk_items WHERE id = ?", "item-" + id);
        assertThat(itemRow.get("title")).isEqualTo("Untitled");
        assertThat(itemRow.get("status")).isEqualTo("watching");
        assertThat(itemRow.get("exam_date")).isNull();
        Map<String, Object> badRow = jdbc.queryForMap(
                "SELECT exam_date, last_date FROM desk_items WHERE id = ?", "bad-" + id);
        assertThat(badRow.get("exam_date")).isNull();
        assertThat(badRow.get("last_date")).isNull();
    }

    @Test
    void corruptJsonIsSkipped() throws Exception {
        props.getStudent().setImportJson(true);
        Files.writeString(tmp.resolve("students.json"), "{");
        importer.importIfEmpty();
        assertThat(students.studentCount()).isZero();
    }

    private static DriverManagerDataSource dataSource(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(url);
        ds.setUsername("govtjobs");
        ds.setPassword("govtjobs");
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    private Map<String, Object> student(String id, String email) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("email", email);
        row.put("passwordHash", "hash");
        row.put("createdAt", "2024-01-01T00:00:00Z");
        return row;
    }

    private void writeStudents(Map<String, Object> body) throws Exception {
        new ObjectMapper().writeValue(tmp.resolve("students.json").toFile(), body);
    }
}
