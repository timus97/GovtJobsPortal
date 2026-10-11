package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodePipelinePortTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void htmlCalendarKeepsCellLinksAndMonthExams() {
        String html = """
                <table>
                  <tr><th>Name of examination</th><th>Date of notification</th><th>Last date</th><th>Date of exam</th></tr>
                  <tr>
                    <td><a href="https://upsc.gov.in/examinations/cse-prelims">Civil Services Preliminary Examination 2026</a></td>
                    <td>22.01.2026</td><td>11.02.2026</td><td>24.05.2026</td>
                  </tr>
                  <tr>
                    <td>Combined Geo-Scientist Examination 2026</td>
                    <td></td><td></td><td>Sept-Oct, 2026</td>
                  </tr>
                  <tr>
                    <td><a href="https://www.ibps.in/wp-content/uploads/IBPS_CALENDAR_2026-27_final.pdf">Institute calendar row for clerks</a></td>
                    <td>01.04.2026</td><td>15.04.2026</td><td>01.06.2026</td>
                  </tr>
                </table>
                """;
        assertThat(CalendarPdf.parseHtml("", "https://upsc.gov.in/examinations/exam-calendar")).isEmpty();
        List<Map<String, Object>> rows = CalendarPdf.parseHtml(html, "https://upsc.gov.in/examinations/exam-calendar");
        Map<String, Object> cse = named(rows, "civil services");
        assertThat(cse.get("notificationDate")).isEqualTo("2026-01-22");
        assertThat(cse.get("lastDate")).isEqualTo("2026-02-11");
        assertThat(cse.get("examDate")).isEqualTo("2026-05-24");
        assertThat(cse.get("officialUrl")).isEqualTo("https://upsc.gov.in/examinations/cse-prelims");
        Map<String, Object> geo = named(rows, "geo-scientist");
        assertThat(geo.get("notificationDate")).isNull();
        assertThat(geo.get("lastDate")).isNull();
        assertThat(geo.get("examDate")).isEqualTo("2026-09");
        assertThat(geo.get("officialUrl")).isNull();

        SourceCollect.Result collected = SourceCollect.collect(
                spec("upsc_advt", "Union Public Service Commission", "upsc", List.of("https://upsc.gov.in/examinations/active")),
                page(html),
                "2026-09-29T00:00:00Z");
        assertThat(collected.ok()).isTrue();
        Map<String, Object> stagedCse = recordNamed(collected.records(), "civil services");
        assertThat(stagedCse.get("officialUrl")).isEqualTo("https://upsc.gov.in/examinations/cse-prelims");
        assertThat(stagedCse.get("notificationDate")).isEqualTo("2026-01-22");
        assertThat(stagedCse.get("examDate")).isEqualTo("2026-05-24");
        assertThat(stagedCse.get("collectorVersion")).isEqualTo("calendar-v1");
        assertThat(stagedCse.get("qualification")).isNull();
        Map<String, Object> stagedGeo = recordNamed(collected.records(), "geo-scientist");
        assertThat(stagedGeo.get("notificationDate")).isNull();
        assertThat(stagedGeo.get("examDate")).isEqualTo("2026-09");
        assertThat(stagedGeo.get("officialUrl")).isEqualTo("https://upsc.gov.in/examinations/active");
        Map<String, Object> stamped = recordNamed(collected.records(), "clerks");
        assertThat(String.valueOf(stamped.get("officialUrl"))).doesNotContain("IBPS_CALENDAR_");
        assertThat(stamped.get("officialUrl")).isEqualTo("https://upsc.gov.in/examinations/active");
    }

    @Test
    void sbiAdvertisementBlockAndFallbackLink() {
        String block = """
                <div>
                  Recruitment of Circle Based Officers Advertisement No. CRPD/SCO/2026-27/03
                  Last date to apply: 30.09.2099
                  <a href="https://sbi.co.in/careers/apply-cbo">Apply now</a>
                </div>
                <div>
                  Engagement of retired officers on contract basis Advertisement No. CRPD/RET/2026/01
                  Last date to apply: 30.09.2099
                  <a href="https://sbi.co.in/careers/retired">Apply</a>
                </div>
                """;
        SourceCollect.Result collected = SourceCollect.collect(
                new SourceSpec(
                        "sbi",
                        "SBI Careers",
                        "sbi",
                        "html_scrape",
                        "",
                        "psu",
                        List.of("https://sbi.co.in/careers"),
                        true,
                        "Banking",
                        "job"),
                page(block),
                "2026-09-29T00:00:00Z");
        assertThat(collected.ok()).isTrue();
        Map<String, Object> officers = recordNamed(collected.records(), "circle based");
        assertThat(officers.get("organization")).isEqualTo("State Bank of India");
        assertThat(officers.get("notificationNo")).isEqualTo("CRPD/SCO/2026-27/03");
        assertThat(officers.get("lastDate")).isEqualTo("2099-09-30");
        assertThat(officers.get("officialUrl")).isEqualTo("https://sbi.co.in/careers/apply-cbo");
        assertThat(officers.get("sector")).isEqualTo("Banking");
        assertThat(officers.get("hasExam")).isEqualTo(true);
        assertThat(officers.get("selectionProcess")).isEqualTo("cbt");
        Map<String, Object> retired = recordNamed(collected.records(), "retired");
        assertThat(retired.get("hasExam")).isEqualTo(false);
        assertThat(retired.get("selectionProcess")).isEqualTo("contract_interview");

        SourceCollect.Result fallback = SourceCollect.collect(
                spec("sbi_careers", "State Bank of India", "sbi", List.of("https://sbi.co.in/careers")),
                page("""
                        <a href="https://sbi.co.in/careers/recruitment-of-probationary-officers">Recruitment of Probationary Officers</a>
                        """),
                "2026-09-29T00:00:00Z");
        assertThat(fallback.records()).hasSize(1);
        assertThat(fallback.records().get(0).get("officialUrl"))
                .isEqualTo("https://sbi.co.in/careers/recruitment-of-probationary-officers");
    }

    @Test
    void pscCalendarRowStaysOnTheCellLinkAndAnEmptyPageAsksForPaste() {
        String html = """
                <table>
                  <tr><th>Name of examination</th><th>Exam date</th></tr>
                  <tr>
                    <td><a href="https://psc.kerala.gov.in/notification/cse">Combined State Civil Service Examination</a></td>
                    <td>May 2026</td>
                  </tr>
                </table>
                <a href="https://psc.kerala.gov.in/login">Login</a>
                """;
        SourceCollect.Result collected = SourceCollect.collect(
                spec("psc_kerala", "Kerala PSC", "genericPsc", List.of("https://psc.kerala.gov.in/notifications")),
                page(html),
                "2026-09-29T00:00:00Z");
        assertThat(collected.records()).hasSize(1);
        Map<String, Object> row = collected.records().get(0);
        assertThat(row.get("officialUrl")).isEqualTo("https://psc.kerala.gov.in/notification/cse");
        assertThat(row.get("examDate")).isEqualTo("2026-05");
        assertThat(row.get("collectorVersion")).isEqualTo("calendar-v1");
        assertThat(row.get("organization")).isEqualTo("Kerala PSC");
        assertThat(row.get("selectionProcess")).isEqualTo("written_multi_stage");
        assertThat(row.get("qualification")).isNull();

        SourceCollect.Result empty = SourceCollect.collect(
                spec("becil", "BECIL", "genericBoard", List.of("https://becil.com/careers")),
                page("<html><title>Careers</title></html>"),
                "2026-09-29T00:00:00Z");
        assertThat(empty.kind()).isEqualTo(SourceRunKind.FAILED);
        assertThat(empty.records()).isEmpty();
        assertThat(empty.message()).contains("ops paste-URL");
    }

    @Test
    void runWidePdfCapStopsTheSecondDownload() throws Exception {
        byte[] pdf = pdfLines(List.of(
                "Advertisement for engagement of Junior Engineers.",
                "Applications are invited. No. of posts: 4. Last date to apply: 30.09.2099"));
        String html = """
                <a href="https://example.gov.in/files/one.pdf">Recruitment notification one</a>
                <a href="https://example.gov.in/files/two.pdf">Recruitment notification two</a>
                """;
        SiteClient client = new SiteClient() {
            @Override
            public PageBody page(String url) {
                return new PageBody(url, html);
            }

            @Override
            public byte[] bytes(String url) {
                return pdf;
            }
        };
        SourceCollect.Result collected = SourceCollect.collect(
                spec("demo_board", "Demo", "generic", List.of("https://example.gov.in/careers")),
                client,
                "2026-09-29T00:00:00Z",
                PdfBudget.shared(10, new AtomicInteger(1)));
        assertThat(collected.records()).hasSize(1);
        assertThat(collected.kind()).isEqualTo(SourceRunKind.PARTIAL);
        assertThat(collected.errors()).contains("pdf skipped: PDF cap reached");
        assertThat(collected.records().get(0).get("pdfHash")).isNotNull();
    }

    @Test
    void documentHrefIsProbedBeforeItIsTreatedAsAPdf() throws Exception {
        byte[] pdf = pdfLines(List.of(
                "Advertisement for engagement of Junior Engineers.",
                "Applications are invited. Last date to apply: 30.09.2099"));
        String html = """
                <a href="https://example.gov.in/download/advt-9">Recruitment notification for Junior Engineer</a>
                """;
        SiteClient client = new SiteClient() {
            @Override
            public PageBody page(String url) {
                return new PageBody(url, html);
            }

            @Override
            public boolean documentPdf(String url) {
                return url.contains("/download/");
            }

            @Override
            public byte[] bytes(String url) {
                return pdf;
            }
        };
        SourceCollect.Result collected = SourceCollect.collect(
                spec("demo_board", "Demo", "generic", List.of("https://example.gov.in/careers")),
                client,
                "2026-09-29T00:00:00Z");
        assertThat(collected.records()).hasSize(1);
        assertThat(collected.records().get(0).get("collectorVersion")).isEqualTo("pdf-v1");
    }

    @Test
    void sourceTimeoutLeavesThePreviousSnapshot(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data/sources"));
        Files.writeString(
                root.resolve("data/sources/registry.json"),
                """
                {"sources":[{"sourceId":"demo_board","name":"Demo","enabled":true,"method":"html_scrape","listUrls":["https://example.gov.in/careers"]}]}
                """);
        Path staging = root.resolve("data/staging/demo_board");
        Files.createDirectories(staging);
        byte[] planted = "[{\"title\":\"keep\"}]".getBytes();
        Files.write(staging.resolve("1000.json"), planted);
        SiteClient client = url -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return new SiteClient.PageBody(
                    url,
                    "<a href=\"https://example.gov.in/advt-9\">Recruitment notification for Junior Engineer</a>");
        };
        CollectOrchestrator.RunOutcome outcome =
                CollectOrchestrator.sourceWithin(root, "demo_board", client, Duration.ofMillis(200));
        Thread.sleep(400);
        assertThat(outcome.sources()).hasSize(1);
        assertThat(outcome.sources().get(0).kind()).isEqualTo(SourceRunKind.FAILED);
        assertThat(outcome.sources().get(0).attempted()).isTrue();
        assertThat(outcome.sources().get(0).records()).isZero();
        assertThat(outcome.sources().get(0).message()).contains("source worker crashed or timed out");
        assertThat(Files.readAllBytes(staging.resolve("1000.json"))).isEqualTo(planted);
        try (var children = Files.list(staging)) {
            assertThat(children.map(path -> path.getFileName().toString()).toList()).containsExactly("1000.json");
        }
    }

    @Test
    void orchestratorArchivesHtmlAndWritesProgress(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data/sources"));
        Files.writeString(
                root.resolve("data/sources/registry.json"),
                """
                {"sources":[{"sourceId":"demo_board","name":"Demo","enabled":true,"method":"html_scrape","listUrls":["https://example.gov.in/careers"]}]}
                """);
        CollectOrchestrator.source(
                root, "demo_board", url -> new SiteClient.PageBody(url, "<html><title>Careers</title></html>"));
        JsonNode progress = mapper.readTree(root.resolve("data/processed/collect-progress.json").toFile());
        assertThat(progress.path("running").asBoolean()).isFalse();
        assertThat(progress.path("sourcesDone").asInt()).isEqualTo(1);
        assertThat(progress.path("sourcesZero").asInt()).isEqualTo(1);
        assertThat(progress.path("runId").asText()).doesNotContain(":");
        try (var runs = Files.list(root.resolve("data/raw/demo_board"))) {
            Path run = runs.findFirst().orElseThrow();
            try (var pages = Files.list(run)) {
                assertThat(pages.filter(path -> path.getFileName().toString().endsWith(".html")).count()).isEqualTo(1);
            }
        }
        assertThatThrownBy(() -> JsoupPages.contentType("http://example.gov.in/download/advt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("non-https");
    }

    private static SourceSpec spec(String id, String name, String collector, List<String> urls) {
        return new SourceSpec(id, name, collector, "html_scrape", "", "central", urls, true);
    }

    private static SiteClient page(String html) {
        return url -> new SiteClient.PageBody(url, html);
    }

    private static Map<String, Object> named(List<Map<String, Object>> rows, String needle) {
        return rows.stream()
                .filter(row -> String.valueOf(row.get("name")).toLowerCase().contains(needle))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> recordNamed(List<Map<String, Object>> rows, String needle) {
        return rows.stream()
                .filter(row -> String.valueOf(row.get("title")).toLowerCase().contains(needle))
                .findFirst()
                .orElseThrow();
    }

    private static byte[] pdfLines(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
                float y = 740;
                for (String line : lines) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 11);
                    stream.newLineAtOffset(40, y);
                    stream.showText(line);
                    stream.endText();
                    y -= 20;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
