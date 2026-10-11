package in.govtjobs.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.job.ExamSeriesSchema;
import in.govtjobs.domain.job.JobSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** The same check as the old qa:schema step, using the Java job and exam-series rules. */
final class SchemaCheck {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    private SchemaCheck() {}

    record Report(boolean ok, int jobs, int series, int failed, String message) {}

    static Report check(Path root) {
        Path jobsPath = root.resolve("data").resolve("processed").resolve("jobs.json");
        if (!Files.isRegularFile(jobsPath)) {
            String message = "Missing data/processed/jobs.json — run process first";
            System.err.println(message);
            return new Report(false, 0, 0, 1, message);
        }
        List<Map<String, Object>> jobs;
        try {
            jobs = readArray(jobsPath, "jobs.json");
        } catch (IOException ex) {
            String message = ex.getMessage() == null ? "jobs.json is not a list" : ex.getMessage();
            System.err.println(message);
            return new Report(false, 0, 0, 1, message);
        }
        int failed = 0;
        for (Map<String, Object> job : jobs) {
            List<String> errors = JobSchema.isValidJob(job);
            if (!errors.isEmpty()) {
                failed++;
                System.err.println("FAIL " + label(job.get("id"), job.get("title")) + ": " + String.join(", ", errors));
            }
        }
        Path seriesPath = root.resolve("data").resolve("processed").resolve("exam_series.json");
        int seriesCount = 0;
        if (Files.isRegularFile(seriesPath)) {
            List<Map<String, Object>> series;
            try {
                series = readArray(seriesPath, "exam_series.json");
            } catch (IOException ex) {
                String message = ex.getMessage() == null ? "exam_series.json is not a list" : ex.getMessage();
                System.err.println(message);
                return new Report(false, jobs.size(), 0, failed + 1, message);
            }
            seriesCount = series.size();
            for (Map<String, Object> row : series) {
                List<String> errors = ExamSeriesSchema.isValidExamSeries(row);
                if (!errors.isEmpty()) {
                    failed++;
                    System.err.println(
                            "FAIL series " + label(row.get("id"), row.get("name")) + ": " + String.join(", ", errors));
                }
            }
        }
        if (failed > 0) {
            String message = "Schema check failed: " + failed + " issue(s)";
            System.err.println(message);
            return new Report(false, jobs.size(), seriesCount, failed, message);
        }
        String message = "Schema OK: " + jobs.size() + " published jobs, " + seriesCount + " exam series";
        System.out.println(message);
        return new Report(true, jobs.size(), seriesCount, 0, message);
    }

    private static List<Map<String, Object>> readArray(Path file, String name) throws IOException {
        JsonNode node = MAPPER.readTree(file.toFile());
        if (node == null || !node.isArray()) {
            throw new IOException(name + " is not a list");
        }
        List<Map<String, Object>> rows = MAPPER.convertValue(node, LIST);
        return rows == null ? List.of() : rows;
    }

    private static String label(Object id, Object title) {
        if (id != null && !String.valueOf(id).isBlank()) {
            return String.valueOf(id);
        }
        return title == null ? "" : String.valueOf(title);
    }
}
