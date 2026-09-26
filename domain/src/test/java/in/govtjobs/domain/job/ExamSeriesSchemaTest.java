package in.govtjobs.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class ExamSeriesSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void cuetExcluded_andNetApplyNever() {
        assertThat(ExamSeriesSchema.isCuetName("CUET (UG) 2026")).isTrue();
        assertThat(ExamSeriesSchema.isCuetName("Civil Services Examination")).isFalse();
        assertThat(ExamSeriesSchema.isNetName("UGC NET")).isTrue();
        assertThat(ExamSeriesSchema.isNetName("NTA NET June")).isTrue();

        assertThat(ExamSeriesSchema.normalizeExamSeries(Map.of(
                        "board",
                        "NTA",
                        "name",
                        "CUET UG",
                        "officialUrl",
                        "https://cuet.nta.nic.in/")))
                .isNull();

        Map<String, Object> net = ExamSeriesSchema.normalizeExamSeries(Map.of(
                "board", "NTA",
                "name", "UGC NET",
                "officialUrl", "https://ugcnet.nta.nic.in/",
                "id", "ugc-net"));
        assertThat(net).isNotNull();
        assertThat(net.get("applyNever")).isEqualTo(true);
        assertThat(net.get("kind")).isEqualTo("series");

        Map<String, Object> cuetRaw = new LinkedHashMap<>();
        cuetRaw.put("id", "cuet");
        cuetRaw.put("board", "NTA");
        cuetRaw.put("name", "CUET UG");
        cuetRaw.put("officialUrl", "https://cuet.nta.nic.in/");
        assertThat(ExamSeriesSchema.isValidExamSeries(cuetRaw)).contains("CUET is excluded");
    }

    @Test
    void seriesMatchesJob_andCalendarDetection() {
        Map<String, Object> cglSeries = new LinkedHashMap<>();
        cglSeries.put("id", "ssc-cgl");
        cglSeries.put("board", "SSC");
        cglSeries.put("name", "Combined Graduate Level");
        cglSeries.put("applyNever", false);
        cglSeries.put("aliases", List.of());

        Map<String, Object> cglJob = new LinkedHashMap<>();
        cglJob.put("title", "Combined Graduate Level Examination (fixture)");
        cglJob.put("organization", "Staff Selection Commission");
        cglJob.put("sourceId", "seed_manual");

        assertThat(ExamSeriesSchema.seriesMatchesJob(cglSeries, cglJob)).isTrue();

        Map<String, Object> net = new LinkedHashMap<>(cglSeries);
        net.put("name", "UGC NET");
        net.put("applyNever", true);
        assertThat(ExamSeriesSchema.seriesMatchesJob(net, cglJob)).isFalse();

        assertThat(ExamSeriesSchema.looksLikeCalendarRow(
                        Map.of("sourceId", "upsc_calendar", "title", "CSE")))
                .isTrue();
        assertThat(ExamSeriesSchema.looksLikeCalendarRow(
                        Map.of("collectorVersion", "calendar-v1", "hasExam", true)))
                .isTrue();
        assertThat(ExamSeriesSchema.looksLikeCalendarRow(
                        Map.of("sourceId", "becil", "title", "Office Assistant")))
                .isFalse();
    }

    @Test
    @EnabledIf("seedExamSeriesExists")
    void seedExamSeries_hasNetNoCuet() throws Exception {
        Path seed = RepoPaths.root().resolve("data/seed/exam_series.json");
        List<Map<String, Object>> rows =
                MAPPER.readValue(Files.readString(seed), new TypeReference<>() {});
        assertThat(rows.size()).isGreaterThanOrEqualTo(12);
        assertThat(rows.stream().noneMatch(r -> ExamSeriesSchema.isCuetName(r.get("name"))))
                .isTrue();
        Map<String, Object> net =
                rows.stream().filter(r -> "ugc-net".equals(r.get("id"))).findFirst().orElseThrow();
        Map<String, Object> normalized = ExamSeriesSchema.normalizeExamSeries(net);
        assertThat(normalized.get("applyNever")).isEqualTo(true);
        assertThat(ExamSeriesSchema.isValidExamSeries(normalized)).isEmpty();
    }

    static boolean seedExamSeriesExists() {
        return Files.isRegularFile(RepoPaths.root().resolve("data/seed/exam_series.json"));
    }
}
