package in.govtjobs.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobSchemaTest {

    @Test
    void classifySelectionText_examFirstThenInclude() {
        JobSchema.ClassificationResult exam =
                JobSchema.classifySelectionText("SSC CGL computer based test");
        assertThat(exam.hasExam()).isTrue();
        assertThat(exam.selectionProcess()).isIn("cbt", "written_multi_stage");
        assertThat(exam.reason()).isEqualTo("exam_keyword");

        // Exam keyword wins even when walk-in also appears later in the blob.
        JobSchema.ClassificationResult examWins =
                JobSchema.classifySelectionText("walk-in after computer based test");
        assertThat(examWins.hasExam()).isTrue();
        assertThat(examWins.selectionProcess()).isEqualTo("cbt");

        JobSchema.ClassificationResult walkIn =
                JobSchema.classifySelectionText("walk-in interview only");
        assertThat(walkIn.hasExam()).isFalse();
        assertThat(walkIn.selectionProcess()).isIn("walk_in", "interview_only");
        assertThat(walkIn.reason()).isEqualTo("include_keyword");

        JobSchema.ClassificationResult unknown = JobSchema.classifySelectionText("something else");
        assertThat(unknown.hasExam()).isNull();
        assertThat(unknown.selectionProcess()).isNull();
        assertThat(unknown.reason()).isEqualTo("unknown");
    }

    @Test
    void computeStatus_nullOpen_andClosingSoonWithin7Days() {
        assertThat(JobSchema.computeStatus(null)).isEqualTo("open");
        assertThat(JobSchema.computeStatus("")).isEqualTo("open");
        assertThat(JobSchema.computeStatus("not-a-date")).isEqualTo("open");

        LocalDate today = LocalDate.of(2026, 9, 12);
        assertThat(JobSchema.computeStatus("2026-09-12", today)).isEqualTo("closing_soon");
        assertThat(JobSchema.computeStatus("2026-09-19", today)).isEqualTo("closing_soon");
        assertThat(JobSchema.computeStatus("2026-09-20", today)).isEqualTo("open");
        assertThat(JobSchema.computeStatus("2026-09-11", today)).isEqualTo("closed");
    }

    @Test
    void stableJobId_sha256First16HexLowercase() {
        String id = JobSchema.stableJobId(
                "Staff Selection Commission",
                "Combined Graduate Level",
                "2026-09-30",
                "https://ssc.gov.in/");
        assertThat(id).hasSize(16).matches("[0-9a-f]{16}");

        String same = JobSchema.stableJobId(Map.of(
                "organization", "STAFF SELECTION COMMISSION",
                "title", "Combined Graduate Level",
                "lastDate", "2026-09-30",
                "officialUrl", "https://ssc.gov.in/"));
        assertThat(same).isEqualTo(id);

        String different = JobSchema.stableJobId(
                "Staff Selection Commission",
                "Combined Graduate Level",
                "2026-10-01",
                "https://ssc.gov.in/");
        assertThat(different).isNotEqualTo(id);
    }

    @Test
    void isValidJob_requiresCoreFields() {
        Map<String, Object> job = validJob();
        assertThat(JobSchema.isValidJob(job)).isEmpty();

        Map<String, Object> missingTitle = new LinkedHashMap<>(job);
        missingTitle.put("title", "");
        List<String> errors = JobSchema.isValidJob(missingTitle);
        assertThat(errors).isNotEmpty();
        assertThat(errors.stream().anyMatch(e -> e.toLowerCase().contains("title"))).isTrue();

        assertThat(JobSchema.SELECTION_PROCESSES).contains("cbt");
    }

    private static Map<String, Object> validJob() {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("id", "j1");
        job.put("title", "Junior Engineer");
        job.put("organization", "SSC");
        job.put("orgType", "central");
        job.put("selectionProcess", "written_multi_stage");
        job.put("hasExam", true);
        job.put("officialUrl", "https://ssc.gov.in/");
        job.put("sourceId", "seed");
        job.put("sourceName", "Seed");
        job.put("sourceUrl", "https://ssc.gov.in/");
        job.put("status", "open");
        return job;
    }
}
