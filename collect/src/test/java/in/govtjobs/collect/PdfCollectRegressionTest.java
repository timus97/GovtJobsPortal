package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Calendar PDF rows and notice-PDF evidence. No network. */
class PdfCollectRegressionTest {

    private static final String LISTING = "https://upsc.gov.in/examinations/exam-calendar";
    private static final String CALENDAR_PDF = "https://upsc.gov.in/sites/default/files/exam_calendar_2026.pdf";
    private static final String CALENDAR_HTML = """
            <html><body>
            <p><a href="/sites/default/files/exam_calendar_2026.pdf">Annual Calendar 2026 (PDF)</a></p>
            <p><a href="/examinations/notice-42.pdf">Notification for Junior Engineer</a></p>
            </body></html>
            """;
    private static final String ONE_CALENDAR = """
            <html><body>
            <p><a href="/sites/default/files/exam_calendar_2026.pdf">Annual Calendar 2026 (PDF)</a></p>
            </body></html>
            """;
    private static final String TEXT_FIXTURE = """
            UNION PUBLIC SERVICE COMMISSION
            Annual Calendar
            Name of Examination    Date of Notification    Last Date for Receipt of Applications    Date of commencement of Exam
            Civil Services Preliminary Examination, 2026    22.01.2026    11.02.2026    24.05.2026
            NDA and NA Examination I, 2026    11.12.2025    31.12.2025    12.04.2026
            Combined Geo-Scientist Examination, 2026    Sept-Oct, 2026
            Page 1
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void calendarTextUsesNotificationThenLastDateThenExam() {
        assertThat(CalendarPdf.parseText("", LISTING)).isEmpty();
        List<Map<String, Object>> rows = CalendarPdf.parseText(TEXT_FIXTURE, LISTING);
        Map<String, Object> civil = rowNamed(rows, "civil services");
        assertThat(civil.get("notificationDate")).isEqualTo("2026-01-22");
        assertThat(civil.get("lastDate")).isEqualTo("2026-02-11");
        assertThat(civil.get("examDate")).isEqualTo("2026-05-24");
        assertThat(civil.get("officialUrl")).isEqualTo(LISTING);
        Map<String, Object> nda = rowNamed(rows, "nda");
        assertThat(nda.get("notificationDate")).isEqualTo("2025-12-11");
        assertThat(nda.get("lastDate")).isEqualTo("2025-12-31");
        assertThat(nda.get("examDate")).isEqualTo("2026-04-12");
        Map<String, Object> geo = rowNamed(rows, "geo-scientist");
        assertThat(geo.get("notificationDate")).isEqualTo("2026-09");
        assertThat(geo.get("lastDate")).isNull();
        assertThat(geo.get("examDate")).isNull();
    }

    @Test
    void calendarPdfLinkIsFoundWhenTheJobScoreDropsIt() {
        List<Map<String, Object>> links = CalendarPdf.findLinks(CALENDAR_HTML, LISTING);
        assertThat(links).extracting(link -> link.get("href"))
                .contains(CALENDAR_PDF, "https://upsc.gov.in/examinations/notice-42.pdf");
        assertThat(JobLinkQuality.isKeepableJobLink(Map.of(
                        "title", "Annual Calendar 2026 (PDF)",
                        "href", CALENDAR_PDF)))
                .isFalse();
        List<Map<String, Object>> elsewhere = CalendarPdf.findLinks(
                """
                <a href="https://example.gov.in/recruitment/advt-9.pdf">Notification for Junior Engineer</a>
                <a href="https://example.gov.in/files/exam-timetable-2026.pdf">Time table</a>
                """,
                "https://example.gov.in/careers");
        assertThat(elsewhere).extracting(link -> link.get("href"))
                .containsExactly("https://example.gov.in/files/exam-timetable-2026.pdf");
    }

    @Test
    void parsedCalendarPdfBecomesASeriesOnTheListingPage(@TempDir Path root) throws Exception {
        byte[] calendar = pdfLines(List.of(
                "UNION PUBLIC SERVICE COMMISSION",
                "Civil Services Preliminary Examination, 2026 22.01.2026 11.02.2026 24.05.2026",
                "NDA and NA Examination I, 2026 11.12.2025 31.12.2025 12.04.2026",
                "Combined Geo-Scientist Examination, 2026 Sept-Oct, 2026"));
        FixtureClient client = new FixtureClient(ONE_CALENDAR);
        client.files.put(CALENDAR_PDF, calendar);
        SourceCollect.Result collected = SourceCollect.collect(upsc(), client, "2026-09-29T00:00:00Z");
        assertThat(collected.kind()).isEqualTo(SourceRunKind.COMPLETE);
        assertThat(collected.errors()).isEmpty();
        assertThat(collected.records()).allSatisfy(row -> {
            assertThat(row.get("officialUrl")).isEqualTo(LISTING);
            assertThat(row.get("qualification")).isNull();
            assertThat(row.get("collectorVersion")).isEqualTo("calendar-v1");
        });
        Map<String, Object> nda = rowNamed(collected.records(), "nda");
        assertThat(nda.get("lastDate")).isEqualTo("2025-12-31");
        assertThat(nda.get("examDate")).isEqualTo("2026-04-12");
        Map<String, Object> geo = rowNamed(collected.records(), "geo-scientist");
        assertThat(geo.get("notificationDate")).isEqualTo("2026-09");
        assertThat(geo.get("lastDate")).isNull();

        writeJson(root.resolve("data/staging/upsc_calendar/one.json"), collected.records());
        BuildJobs.run(root, new BuildJobs.Options(false));
        String series = Files.readString(root.resolve("data/processed/exam_series.json"));
        String jobs = Files.readString(root.resolve("data/processed/jobs.json"));
        assertThat(series).contains("Geo-Scientist");
        assertThat(series).contains(LISTING);
        assertThat(series).doesNotContain("exam_calendar_2026.pdf");
        assertThat(jobs).doesNotContain("Geo-Scientist");
        JsonNode match = null;
        for (JsonNode row : mapper.readTree(root.resolve("data/processed/exam_series.json").toFile())) {
            if (row.path("name").asText().toLowerCase().contains("geo-scientist")) {
                match = row;
            }
        }
        assertThat(match).isNotNull();
        assertThat(match.path("minEducation").isNull()).isTrue();
        assertThat(match.path("officialUrl").asText()).isEqualTo(LISTING);
        assertThat(match.path("expectedNotify").asText()).isEqualTo("2026-09");
    }

    @Test
    void calendarPdfTimeoutAndFailedFetchDoNotInventRows(@TempDir Path root) throws Exception {
        writeRegistry(root, "upsc_calendar", "upsc", "UPSC", List.of(LISTING));
        Path previous = plant(root, "upsc_calendar");
        byte[] before = Files.readAllBytes(previous);

        CollectOrchestrator.RunOutcome timedOut = CollectOrchestrator.source(root, "upsc_calendar", new FixtureClient(ONE_CALENDAR) {
            @Override
            public byte[] bytes(String url) throws IOException {
                throw new IOException("Read timed out");
            }
        });
        assertThat(timedOut.sources().get(0).kind()).isEqualTo(SourceRunKind.FAILED);
        assertThat(timedOut.sources().get(0).records()).isZero();
        assertThat(timedOut.sources().get(0).message()).contains("calendar PDF parse failed");
        assertThat(timedOut.sources().get(0).message()).contains("timed out");
        assertThat(outcome(root)).isEqualTo("failed");
        assertThat(Files.readAllBytes(previous)).isEqualTo(before);

        CollectOrchestrator.RunOutcome missing = CollectOrchestrator.source(root, "upsc_calendar", new FixtureClient(ONE_CALENDAR) {
            @Override
            public byte[] bytes(String url) throws IOException {
                throw new IOException("HTTP 404");
            }
        });
        assertThat(missing.sources().get(0).kind()).isEqualTo(SourceRunKind.FAILED);
        assertThat(missing.sources().get(0).message()).contains("HTTP 404");
        assertThat(Files.readAllBytes(previous)).isEqualTo(before);
        assertThat(jsonNames(previous.getParent())).containsExactly("1000.json");

        FixtureClient garbage = new FixtureClient(ONE_CALENDAR);
        garbage.files.put(CALENDAR_PDF, "not-a-pdf".getBytes());
        SourceCollect.Result unreadable = SourceCollect.collect(upsc(), garbage, "2026-09-29T00:00:00Z");
        assertThat(unreadable.records()).isEmpty();
        assertThat(unreadable.kind()).isEqualTo(SourceRunKind.FAILED);
        assertThat(unreadable.errors()).contains("calendar PDF parse failed: pdf_parse_failed");
    }

    @Test
    void noticePdfIsKeptOnlyWhenTheTextIsRecruitmentEvidence() throws Exception {
        String page = "https://example.gov.in/careers";
        String href = "https://example.gov.in/recruitment/advt-9.pdf";
        String html = "<a href=\"" + href + "\">Notification for Junior Engineer advt no 9</a>";
        FixtureClient kept = new FixtureClient(html);
        kept.files.put(href, pdfLines(List.of(
                "Advertisement for engagement of Consultant",
                "Applications are invited",
                "No. of posts: 12",
                "Last date for receipt of applications: 30.09.2099")));
        SourceCollect.Result evidence = SourceCollect.collect(board(page), kept, "2026-09-30T00:00:00Z");
        assertThat(evidence.kind()).isEqualTo(SourceRunKind.COMPLETE);
        assertThat(evidence.records()).hasSize(1);
        Map<String, Object> row = evidence.records().get(0);
        assertThat(row.get("collectorVersion")).isEqualTo("pdf-v1");
        assertThat(row.get("officialUrl")).isEqualTo(href);
        assertThat(row.get("lastDate")).isEqualTo("2099-09-30");
        assertThat(row.get("vacancies")).isEqualTo(12);

        FixtureClient tender = new FixtureClient(html);
        tender.files.put(href, pdfLines(List.of("Notice Inviting Tender for furniture. Bid document.")));
        SourceCollect.Result dropped = SourceCollect.collect(board(page), tender, "2026-09-30T00:00:00Z");
        assertThat(dropped.records()).isEmpty();
        assertThat(dropped.kind()).isEqualTo(SourceRunKind.FAILED);
        assertThat(dropped.errors()).contains("pdf skipped: pdf_not_recruitment");

        StringBuilder many = new StringBuilder();
        FixtureClient capped = new FixtureClient("");
        byte[] notice = pdfLines(List.of("Advertisement for engagement of Consultant. Applications are invited. No. of posts: 2"));
        for (int i = 1; i <= 11; i++) {
            String url = "https://example.gov.in/recruitment/advt-" + i + ".pdf";
            many.append("<a href=\"").append(url).append("\">Recruitment notification advt no ").append(i).append("</a>");
            capped.files.put(url, notice);
        }
        capped.html = many.toString();
        SourceCollect.Result overCap = SourceCollect.collect(board(page), capped, "2026-09-30T00:00:00Z");
        assertThat(overCap.records()).hasSize(10);
        assertThat(overCap.kind()).isEqualTo(SourceRunKind.PARTIAL);
        assertThat(overCap.errors()).contains("pdf skipped: PDF cap reached");
    }

    @Test
    void noticePdfTimeoutAndFailedFetchAreNotPublished(@TempDir Path root) throws Exception {
        String page = "https://example.gov.in/careers";
        String html = "<a href=\"https://example.gov.in/recruitment/advt-9.pdf\">Notification for Junior Engineer advt no 9</a>";
        writeRegistry(root, "demo_board", "generic", "Demo", List.of(page));
        Path previous = plant(root, "demo_board");
        byte[] before = Files.readAllBytes(previous);

        CollectOrchestrator.RunOutcome timedOut = CollectOrchestrator.source(root, "demo_board", new FixtureClient(html) {
            @Override
            public byte[] bytes(String url) throws IOException {
                throw new IOException("Read timed out");
            }
        });
        assertThat(timedOut.sources().get(0).kind()).isEqualTo(SourceRunKind.FAILED);
        assertThat(timedOut.sources().get(0).records()).isZero();
        assertThat(timedOut.sources().get(0).message()).contains("pdf skipped");
        assertThat(timedOut.sources().get(0).message()).contains("timed out");
        assertThat(outcome(root)).isEqualTo("failed");
        assertThat(Files.readAllBytes(previous)).isEqualTo(before);

        CollectOrchestrator.RunOutcome failed = CollectOrchestrator.source(root, "demo_board", new FixtureClient(html) {
            @Override
            public byte[] bytes(String url) throws IOException {
                throw new IOException("HTTP 503");
            }
        });
        assertThat(failed.sources().get(0).kind()).isEqualTo(SourceRunKind.FAILED);
        assertThat(failed.sources().get(0).message()).contains("HTTP 503");
        assertThat(failed.sources().get(0).records()).isZero();
        assertThat(jsonNames(previous.getParent())).containsExactly("1000.json");
        assertThat(Files.readAllBytes(previous)).isEqualTo(before);
    }

    private static Map<String, Object> rowNamed(List<Map<String, Object>> rows, String needle) {
        return rows.stream()
                .filter(row -> String.valueOf(row.get("title") != null ? row.get("title") : row.get("name"))
                        .toLowerCase()
                        .contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + needle + " in " + rows));
    }

    private static SourceSpec upsc() {
        return new SourceSpec(
                "upsc_calendar", "UPSC", "upsc", "html_scrape", "", "central", List.of(LISTING), true);
    }

    private static SourceSpec board(String page) {
        return new SourceSpec("demo_board", "Demo", "generic", "html_scrape", "", "central", List.of(page), true);
    }

    private void writeRegistry(Path root, String id, String collector, String name, List<String> urls) throws IOException {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceId", id);
        source.put("name", name);
        source.put("collector", collector);
        source.put("method", "html_scrape");
        source.put("enabled", true);
        source.put("orgTypeDefault", "central");
        source.put("listUrls", urls);
        writeJson(root.resolve("data/sources/registry.json"), Map.of("sources", List.of(source)));
    }

    private Path plant(Path root, String sourceId) throws IOException {
        Path file = root.resolve("data/staging").resolve(sourceId).resolve("1000.json");
        writeJson(file, List.of(Map.of(
                "title", "Old board recruitment advt",
                "officialUrl", "https://example.gov.in/advt/old")));
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000L));
        return file;
    }

    private void writeJson(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
    }

    private String outcome(Path root) throws IOException {
        return mapper.readTree(root.resolve("data/processed/collect-report.json").toFile())
                .path("sources")
                .get(0)
                .path("outcome")
                .asText();
    }

    private static List<String> jsonNames(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json") || name.endsWith(".jsonl"))
                    .sorted()
                    .toList();
        }
    }

    private static byte[] pdfLines(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
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

    private static class FixtureClient implements SiteClient {
        private String html;
        private final Map<String, byte[]> files = new LinkedHashMap<>();

        private FixtureClient(String html) {
            this.html = html;
        }

        @Override
        public PageBody page(String url) {
            return new PageBody(url, html);
        }

        @Override
        public byte[] bytes(String url) throws IOException {
            byte[] body = files.get(url);
            if (body == null) {
                throw new IOException("pdf download not available");
            }
            return body;
        }
    }
}
