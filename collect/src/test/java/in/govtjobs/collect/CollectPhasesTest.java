package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import in.govtjobs.domain.RepoPaths;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectPhasesTest {

    private static final String FIXTURE_HTML = """
            <a href="/login">Login</a>
            <a href="https://example.gov.in/">Home</a>
            <a href="https://example.gov.in/tender/contract-award">Contract Award</a>
            <a href="https://example.gov.in/recruitment/advt-42-2026.pdf">Notification for Junior Engineer</a>
            <a href="https://example.gov.in/careers">Careers</a>
            """;

    private static final String CALENDAR_HTML = """
            <html><body>
            <table>
              <tr><th>Name of examination</th><th>Exam date</th></tr>
              <tr><td>CRP PO MT XVI</td><td>May 2026</td></tr>
            </table>
            <a href="https://www.ibps.in/wp-content/uploads/IBPS_CALENDAR_2026-27_final.pdf">IBPS calendar</a>
            <a href="https://www.ibps.in/crp-csa">Apply Online for CRP-CSA-XVI</a>
            </body></html>
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void htmlFixtureKeepsTheSameLinksAndDropsTheSameTitles(@TempDir Path dir) throws Exception {
        List<Map<String, Object>> links =
                HtmlLinks.extractLinks(FIXTURE_HTML, "https://example.gov.in/careers", true, 20);
        List<String> hrefs = JobLinkQuality.filterJobLinks(links).stream()
                .map(link -> String.valueOf(link.get("href")))
                .toList();
        List<Map<String, String>> samples = List.of(
                Map.of("title", "Login", "href", "https://www.ncs.gov.in/login"),
                Map.of("title", "Post New Jobs", "href", "https://betacloud.ncs.gov.in/home/job-post"),
                Map.of("title", "Tender Notification", "href", "https://tenders.bhel.com/tenders"),
                Map.of("title", "Working at BHEL", "href", "https://careers.bhel.in/"),
                Map.of(
                        "title",
                        "Ingots",
                        "href",
                        "https://nalcoindia.com/wp-content/uploads/2019/04/INGOT-Specification.pdf"),
                Map.of("title", "Skip to main content", "href", "https://iocl.com/latest-job-opening"),
                Map.of(
                        "title",
                        "हिंदी",
                        "href",
                        "https://iocl.com/Language?ReturnUrl=%2Flatest-job-opening&handler=Return"),
                Map.of(
                        "title",
                        "Updated On 20-12-2023",
                        "href",
                        "https://www.aai.aero/en/careers/recruitment/release/396317"));
        List<String> dropped = new ArrayList<>();
        for (Map<String, String> sample : samples) {
            if (!JobLinkQuality.isKeepableJobLink(sample)) {
                dropped.add(sample.get("title"));
            }
        }
        JsonNode node = mapper.readTree(nodeQuality(dir, samples));
        assertThat(hrefs).containsExactlyElementsOf(stringList(node.get("hrefs")));
        assertThat(dropped).containsExactlyElementsOf(stringList(node.get("dropped")));
        assertThat(hrefs).containsExactly("https://example.gov.in/recruitment/advt-42-2026.pdf");
        assertThat(dropped).contains("Login", "Skip to main content", "हिंदी", "Updated On 20-12-2023");
        assertThat(JobLinkQuality.isKeepableJobLink(Map.of(
                        "title",
                        "Click Here to Read the Detailed Advertisement - [14/08/2026]",
                        "href",
                        "https://iocl.com/admin/img/UploadedFiles/LatestJobOpening/Files/DetailedAd14082026.pdf")))
                .isTrue();
        assertThat(JobLinkQuality.isKeepableJobLink(Map.of(
                        "title", "Click here", "href", "https://iocl.com/latest-job-opening/advertisement.pdf")))
                .isFalse();
        assertThat(JobLinkQuality.isKeepableJobLink(Map.of(
                        "title", "View Details", "href", "https://www.nhpcindia.com/welcome/job")))
                .isFalse();

        String notice = """
                GOVERNMENT OF INDIA
                Advertisement for engagement of Consultant (IT)
                Applications are invited for walk-in interview
                Last date for receipt of applications: 30.09.2026
                No. of posts: 12
                """;
        assertThat(JobLinkQuality.isRecruitmentPdfText(notice)).isTrue();
        assertThat(JobLinkQuality.isRecruitmentPdfText("Notice Inviting Tender (NIT) for supply of furniture. Bid document."))
                .isFalse();
        assertThat(JobLinkQuality.isRecruitmentPdfText("NCS employer portal user manual. Registration flow for ISF login."))
                .isFalse();
        Map<String, Object> fields = PdfDocuments.parseFields(notice);
        assertThat(fields.get("isJobNotice")).isEqualTo(true);
        assertThat(fields.get("vacancies")).isEqualTo(12);
        assertThat(fields.get("lastDate")).isEqualTo("2026-09-30");
        assertThat(NoticeDates.findLastDateHint("Updated On 20-12-2023")).isNull();
        assertThat(NoticeDates.findLastDateHint("Last date to apply: 20-12-2023")).isEqualTo("2023-12-20");
        assertThat(NoticeDates.findLastDateHint("Applications extended till 14-Oct-2026")).isEqualTo("2026-10-14");
        assertThat(NoticeDates.parseDateFromText("14-Oct-2026")).isEqualTo("2026-10-14");
        assertThat(JsoupPages.sameHost("https://ssc.gov.in/a", "https://www.ssc.gov.in/b")).isTrue();
        assertThat(JsoupPages.sameHost("https://ssc.gov.in/a", "https://example.gov.in/b")).isFalse();
        assertThat(JsoupPages.sameHost("https://www.iocl.com/latest-job-opening", "https://careers.iocl.com/jobs"))
                .isTrue();
        assertThat(JsoupPages.sameHost("https://iocl.co.in/a", "https://careers.iocl.co.in/b")).isTrue();
        assertThat(JsoupPages.sameHost("https://ssc.gov.in/a", "https://upsc.gov.in/b")).isFalse();
        assertThat(JsoupPages.sameHost("https://www.bhel.com/a", "https://careers.bhel.in/b")).isFalse();
        assertThat(JsoupPages.sameHost("https://example.gov.in/a", "http://127.0.0.1/b")).isFalse();
        assertThat(JsoupPages.sameHost("http://127.0.0.1/a", "http://127.0.0.2/b")).isFalse();
        assertThat(node.get("updated").isNull()).isTrue();
        assertThat(node.get("labeled").asText()).isEqualTo("2023-12-20");
        assertThat(StagingRecords.cleanTitle("Engagement of Technician ( | PDF | 1.1 KB | English)"))
                .doesNotContain("PDF")
                .doesNotContain("1.1 KB");
        assertThat(StagingRecords.cleanTitle(
                        "Walk in Interview for recruitment of Specialists- PDF size:(1.13 MB) ."))
                .isEqualTo("Walk in Interview for recruitment of Specialists");
    }

    @Test
    void calendarRowBecomesASeriesAndAYearStampedPdfIsNotThisYearsNotice(@TempDir Path root) throws Exception {
        SourceSpec calendar = spec(
                "ibps_calendar",
                "IBPS",
                "ibps",
                "html_scrape",
                "",
                List.of("https://www.ibps.in/index.php/crp-updates/"));
        RecordingClient client = new RecordingClient(Map.of(calendar.listUrls().get(0), CALENDAR_HTML));
        SourceCollect.Result collected = SourceCollect.collect(calendar, client, "2026-09-29T00:00:00Z");
        assertThat(collected.ok()).isTrue();
        assertThat(collected.records()).isNotEmpty();
        assertThat(collected.records())
                .noneMatch(row -> String.valueOf(row.get("officialUrl")).contains("IBPS_CALENDAR_"));
        assertThat(collected.records())
                .allSatisfy(row -> assertThat(row.get("eligibilityParse")).isEqualTo(Map.of("complete", false)));
        writeJson(root.resolve("data/staging/ibps_calendar/one.json"), collected.records());
        BuildJobs.run(root, new BuildJobs.Options(false));
        JsonNode series = mapper.readTree(root.resolve("data/processed/exam_series.json").toFile());
        JsonNode match = null;
        for (JsonNode row : series) {
            if ("CRP PO MT XVI".equals(row.path("name").asText())) {
                match = row;
            }
        }
        assertThat(match).isNotNull();
        assertThat(match.path("board").asText()).isEqualTo("IBPS");
        assertThat(match.path("expectedExam").asText()).isEqualTo("May 2026");
        assertThat(match.path("minEducation").isNull()).isTrue();
        JsonNode jobs = mapper.readTree(root.resolve("data/processed/jobs.json").toFile());
        assertThat(jobs.findValuesAsText("title")).doesNotContain("CRP PO MT XVI");

        SourceSpec stamped = spec(
                "ibps_calendar",
                "IBPS",
                "ibps",
                "html_scrape",
                "",
                List.of("https://www.ibps.in/wp-content/uploads/IBPS_CALENDAR_2026-27_final.pdf"));
        RecordingClient unused = new RecordingClient(Map.of());
        SourceCollect.Result rejected = SourceCollect.collect(stamped, unused, "2026-09-29T00:00:00Z");
        assertThat(rejected.ok()).isFalse();
        assertThat(rejected.records()).isEmpty();
        assertThat(rejected.message()).isEqualTo(SourceRules.YEAR_STAMPED_PDF);
        assertThat(unused.pages).isZero();
        assertThat(SourceRules.isYearStampedCalendarPdf("https://www.ibps.in/index.php/crp-updates/"))
                .isFalse();
    }

    @Test
    void brochurePdfsAreDroppedAndARealNotificationIsKept() throws Exception {
        String html = """
                <a href="https://www.bemlindia.in/wp-content/uploads/2024/08/BEML-Journey.pdf">BEML Journey</a>
                <a href="https://www.hudco.org.in/writereaddata/orgchart.pdf">Organogram</a>
                <a href="https://grse.in/career/PDFs/Detailed_Advt_Expert_2025_03.pdf">GRSE Employment Notification No. 2025/03(E) (Detailed Notification)</a>
                """;
        RecordingClient client = new RecordingClient(Map.of("https://example.gov.in/careers", html));
        SourceCollect.Result collected = SourceCollect.collect(
                spec("demo_board", "Demo", "generic", "html_scrape", "", List.of("https://example.gov.in/careers")),
                client,
                "2026-09-30T00:00:00Z");
        assertThat(collected.records()).extracting(row -> row.get("title"))
                .contains("GRSE Employment Notification No. 2025/03(E) (Detailed Notification)")
                .doesNotContain("BEML Journey", "Organogram");
        assertThat(JobLinkQuality.rejectedDocument("Notice Inviting Tender for furniture")).isTrue();
        assertThat(JobLinkQuality.rejectedDocument("Advertisement for engagement of Consultant. Applications are invited.")).isFalse();
    }

    @Test
    void tableRowDateIsKeptAndABrowserBoardDoesNotFallBackToHtml() throws Exception {
        String html = """
                <table><tr>
                  <td><a href="https://example.gov.in/recruitment/je-2026">Junior Engineer recruitment</a></td>
                  <td>Last date 14-Oct-2026</td>
                </tr></table>
                """;
        RecordingClient client = new RecordingClient(Map.of("https://example.gov.in/careers", html));
        SourceCollect.Result collected = SourceCollect.collect(
                spec("demo_board", "Demo", "generic", "html_scrape", "", List.of("https://example.gov.in/careers")),
                client,
                "2026-09-30T00:00:00Z");
        assertThat(collected.records()).anyMatch(row -> "2026-10-14".equals(row.get("lastDate")));

        RecordingClient rendered = new RecordingClient(Map.of("https://ssc.gov.in/home/notice-board", "<a href=\"https://ssc.gov.in/should-not-be-read\">ignored</a>"));
        rendered.browserHtml = "<a href=\"https://ssc.gov.in/notice/cgl-2026\">CGL 2026 notification</a>";
        SourceCollect.Result board = SourceCollect.collect(
                spec("ssc_notices", "SSC", "ssc", "browser_scrape", "browser", List.of("https://ssc.gov.in/home/notice-board")),
                rendered,
                "2026-09-30T00:00:00Z");
        assertThat(rendered.pages).isZero();
        assertThat(board.ok()).isTrue();
        assertThat(board.records().get(0).get("organization")).isEqualTo("Staff Selection Commission");
    }

    @Test
    void failedNcsRrbcdgAndDefenceCaptchaStayErrors() throws IOException {
        RecordingClient ncsClient = new RecordingClient(Map.of());
        ncsClient.browserHtml = "<html><a href=\"https://www.ncs.gov.in/\">National Career Service</a></html>";
        SourceCollect.Result ncs = SourceCollect.collect(
                spec("ncs_gov", "NCS", "ncs", "browser_scrape", "browser", List.of("https://www.ncs.gov.in/")),
                ncsClient,
                "2026-09-29T00:00:00Z");
        assertThat(ncs.ok()).isFalse();
        assertThat(ncs.records()).isEmpty();
        assertThat(ncs.message()).isEqualTo(SourceRules.NCS_EMPTY);
        assertThat(ncsClient.pages).isZero();

        RecordingClient emptyNcs = new RecordingClient(Map.of());
        emptyNcs.browserHtml = """
                <html><head><title>NcsNewWebsite</title></head>
                <body><p>0 Job Posts and 0 Vacancies</p></body></html>
                """;
        SourceCollect.Result emptyList = SourceCollect.collect(
                spec(
                        "ncs_gov",
                        "NCS",
                        "ncs",
                        "browser_scrape",
                        "browser",
                        List.of("https://ncs.gov.in/job-listing?isGovernmentJob=true")),
                emptyNcs,
                "2026-09-30T00:00:00Z");
        assertThat(emptyList.ok()).isTrue();
        assertThat(emptyList.records()).isEmpty();
        assertThat(emptyList.message()).contains("0 Job Posts and 0 Vacancies");
        assertThat(emptyList.message()).contains("NcsNewWebsite");

        RecordingClient rrbClient = new RecordingClient(Map.of());
        SourceCollect.Result zonal = SourceCollect.collect(
                spec(
                        "rrb_apply",
                        "RRB",
                        "rrb",
                        "html_scrape",
                        "",
                        List.of("https://www.rrbcdg.gov.in/employment-notices.php")),
                rrbClient,
                "2026-09-29T00:00:00Z");
        assertThat(zonal.ok()).isFalse();
        assertThat(zonal.records()).isEmpty();
        assertThat(zonal.message()).isEqualTo(SourceRules.CHANDIGARH);
        assertThat(rrbClient.pages).isZero();
        assertThat(SourceRules.isNationalApplyHost("https://www.rrbapply.gov.in/")).isTrue();
        assertThat(SourceRules.isChandigarhZonalHost("https://www.rrbapply.gov.in/")).isFalse();
        assertThat(SourceRules.isRrbNoticeLink("CEN 09/2025 Group D Apply", "https://www.rrbapply.gov.in/cen"))
                .isTrue();

        RecordingClient defenceClient = new RecordingClient(Map.of(
                "https://joinindianarmy.nic.in/",
                "<html><div class=\"g-recaptcha\"></div><p>Recruitment of officers advt no 9</p></html>"));
        SourceCollect.Result defence = SourceCollect.collect(
                spec("defence_army", "Army", "", "manual", "", List.of("https://joinindianarmy.nic.in/")),
                defenceClient,
                "2026-09-29T00:00:00Z");
        assertThat(defence.ok()).isFalse();
        assertThat(defence.records()).isEmpty();
        assertThat(defence.message()).isEqualTo(SourceRules.CAPTCHA);
        assertThat(defenceClient.pages).isZero();

        RecordingClient blocked = new RecordingClient(Map.of(
                "https://example.gov.in/jobs",
                "<html>please verify you are human</html>"));
        SourceCollect.Result captcha = SourceCollect.collect(
                spec("demo_board", "Demo Board", "html", "html_scrape", "", List.of("https://example.gov.in/jobs")),
                blocked,
                "2026-09-29T00:00:00Z");
        assertThat(captcha.message()).isEqualTo(SourceRules.CAPTCHA);
        String calendar = """
                <html><script>recaptcha</script><body>
                <h1>Calendar</h1>
                <p>Annual Calendar for the Civil Services Examination and other recruitment notifications.</p>
                <a href="https://www.upsc.gov.in/sites/default/files/Calendar-Year-2027-Engl.pdf">Annual Calendar 2027</a>
                </body></html>
                """;
        assertThat(SourceRules.looksBlocked(calendar)).isFalse();
        assertThat(calendar.length()).isGreaterThan(80);
        assertThat(captcha.records()).isEmpty();

        JsonNode registry = mapper.readTree(RepoPaths.root().resolve("data/sources/registry.json").toFile());
        assertThat(registry.path("sources"))
                .noneMatch(row -> row.path("listUrls").toString().toLowerCase().contains("rrbcdg.gov.in"));
        JsonNode ibps = source(registry, "ibps_calendar");
        assertThat(ibps.path("listUrls").toString()).doesNotContain("IBPS_CALENDAR_");
        for (String id : List.of("defence_army", "defence_navy", "defence_airforce")) {
            JsonNode row = source(registry, id);
            assertThat(row.path("method").asText()).isEqualTo("manual");
            assertThat(row.path("enabled").asBoolean()).isFalse();
            assertThat(row.path("robotsNotes").asText()).containsIgnoringCase("no captcha bypass");
        }
    }

    @Test
    void operatorApprovesOneRowAndAFailedRunKeepsThePreviousCatalog(@TempDir Path root) throws Exception {
        String page = "https://example.gov.in/jobs";
        writeJson(
                root.resolve("data/sources/registry.json"),
                Map.of(
                        "sources",
                        List.of(Map.of(
                                "sourceId",
                                "demo_board",
                                "name",
                                "Demo Board",
                                "collector",
                                "html",
                                "method",
                                "html_scrape",
                                "enabled",
                                true,
                                "orgTypeDefault",
                                "central",
                                "listUrls",
                                List.of(page)))));
        AtomicBoolean sawReport = new AtomicBoolean();
        SiteClient client = url -> {
            JsonNode report = mapper.readTree(root.resolve("data/processed/collect-report.json").toFile());
            assertThat(report.path("usable").asBoolean()).isFalse();
            assertThat(report.path("sources")).isEmpty();
            assertThat(report.path("timeoutSeconds").asInt()).isEqualTo(120);
            sawReport.set(true);
            return new SiteClient.PageBody(
                    url,
                    "<a href=\"https://example.gov.in/advt/field-44\">Field staff advt no 44. Last date to apply: 01-06-2099</a>");
        };
        CollectOrchestrator.RunOutcome fetched = CollectOrchestrator.source(root, "demo_board", client);
        assertThat(sawReport).isTrue();
        assertThat(fetched.sources().get(0).ok()).isTrue();
        assertThat(fetched.sources().get(0).records()).isEqualTo(1);
        BuildJobs.run(root, new BuildJobs.Options(false));
        JsonNode quarantine = mapper.readTree(root.resolve("data/processed/quarantine.json").toFile());
        assertThat(quarantine).hasSize(1);
        assertThat(quarantine.get(0).path("reason").asText()).isEqualTo("needs_review");
        String id = quarantine.get(0).path("job").path("id").asText();
        assertThat(mapper.readTree(root.resolve("data/processed/jobs.json").toFile()).findValuesAsText("id"))
                .doesNotContain(id);

        QuarantineApproval.approve(root, id, "operator confirmed the notice");
        BuildJobs.run(root, new BuildJobs.Options(false));
        JsonNode jobs = mapper.readTree(root.resolve("data/processed/jobs.json").toFile());
        JsonNode published = null;
        for (JsonNode job : jobs) {
            if (id.equals(job.path("id").asText())) {
                published = job;
            }
        }
        assertThat(published).isNotNull();
        assertThat(published.path("approvalReason").asText()).isEqualTo("operator confirmed the notice");
        assertThat(published.path("needsReview").asBoolean()).isFalse();

        Path failed = root.resolve("failed");
        Files.createDirectories(failed.resolve("data/processed"));
        Files.writeString(failed.resolve("data/processed/jobs.json"), "STAY");
        writeJson(
                failed.resolve("data/sources/registry.json"),
                Map.of(
                        "sources",
                        List.of(Map.of(
                                "sourceId",
                                "demo_board",
                                "name",
                                "Demo Board",
                                "method",
                                "html_scrape",
                                "enabled",
                                true,
                                "listUrls",
                                List.of(page)))));
        CollectOrchestrator.daily(
                failed,
                url -> new SiteClient.PageBody(url, "<html><div class=\"g-recaptcha\"></div>verify you are human</html>"));
        assertThat(Files.readString(failed.resolve("data/processed/jobs.json"))).isEqualTo("STAY");
        JsonNode failedReport = mapper.readTree(failed.resolve("data/processed/collect-report.json").toFile());
        assertThat(failedReport.path("usable").asBoolean()).isFalse();
        assertThat(failedReport.path("processed").asBoolean()).isFalse();

        Path clean = root.resolve("clean");
        writeJson(
                clean.resolve("data/sources/registry.json"),
                Map.of(
                        "sources",
                        List.of(Map.of(
                                "sourceId",
                                "demo_board",
                                "name",
                                "Demo Board",
                                "method",
                                "html_scrape",
                                "enabled",
                                true,
                                "listUrls",
                                List.of(page)))));
        CollectOrchestrator.RunOutcome zero = CollectOrchestrator.daily(
                clean, url -> new SiteClient.PageBody(url, "<a href=\"https://example.gov.in/login\">Login</a>"));
        assertThat(zero.sources().get(0).ok()).isTrue();
        assertThat(zero.sources().get(0).message()).isEqualTo(SourceRules.NO_KEEPABLE);
        assertThat(zero.sources().get(0).records()).isZero();
        assertThat(mapper.readTree(clean.resolve("data/processed/collect-report.json").toFile())
                        .path("sources")
                        .get(0)
                        .path("message")
                        .asText())
                .isEqualTo("no keepable notices");
    }

    @Test
    void pdfBytesAreAVacancyOnlyWhenTheTextIsANotice(@TempDir Path root) throws Exception {
        byte[] notice = pdf("Advertisement for engagement of Consultant");
        byte[] tender = pdf("Notice Inviting Tender for furniture");
        assertThat(PdfDocuments.parseFields(PdfDocuments.text(notice)).get("isJobNotice")).isEqualTo(true);
        assertThat(PdfDocuments.parseFields(PdfDocuments.text(tender)).get("isJobNotice")).isEqualTo(false);
        Path stored = PdfDocuments.store(root, notice);
        assertThat(stored.getFileName().toString()).isEqualTo(PdfDocuments.sha256(notice) + ".pdf");
        assertThat(Files.readAllBytes(stored)).isEqualTo(notice);
        assertThat(PlaywrightPages.render(List.of())).isEmpty();
    }

    @Test
    void httpFetchDropsNonHttpsAndRefusesAForeignRedirect() throws Exception {
        assertThatThrownBy(() -> JsoupPages.fetch("http://example.gov.in/jobs"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("non-https");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "<html>ok</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/go", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/away", exchange -> {
            exchange.getResponseHeaders().add("Location", "https://example.gov.in/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/denied", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.createContext("/js", exchange -> {
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/wall", exchange -> {
            exchange.getResponseHeaders()
                    .add(
                            "Location",
                            "https://validate.perfdrive.com/block?ua=NoExamSarkariBot/1.0 (+https://example)");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;
            SiteClient.PageBody ok = JsoupPages.fetch(base + "/go", false);
            assertThat(ok.html()).contains("ok");
            assertThatThrownBy(() -> JsoupPages.fetch(base + "/away", false))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("allowed host");
            assertThatThrownBy(() -> JsoupPages.fetch(base + "/denied", false))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("HTTP 403");
            assertThatThrownBy(() -> JsoupPages.fetch(base + "/js", false))
                    .isInstanceOf(IOException.class)
                    .hasMessage("javascript redirect");
            assertThatThrownBy(() -> JsoupPages.fetch(base + "/wall", false))
                    .isInstanceOf(IOException.class)
                    .hasMessage("blocked: bot manager validate.perfdrive.com");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void employmentHighlightsKeepEachPostAndDoNotDownloadTheEpaper() {
        String html = """
                <table>
                  <tr><th>ORGANISATION</th><th>POST</th><th>METHOD OF APPOINTMENT</th><th>LAST DATE (DD/MM/YYYY)</th></tr>
                  <tr>
                    <td><a href="https://www.fcriindia.com/recruitment/research-engineer">FLUID CONTROL RESEARCH INSTITUTE</a></td>
                    <td>RESEARCH ENGINEER &amp; OTHERS</td>
                    <td>Recruitment</td>
                    <td>24/08/2026</td>
                  </tr>
                  <tr>
                    <td>NATIONAL COUNCIL FOR COOPERATIVE TRAINING</td>
                    <td>CONSULTANT (LEGAL)</td>
                    <td>Recruitment</td>
                    <td>23/08/2026</td>
                  </tr>
                  <tr><td><a href="AllJobs.aspx?k=All">View More</a></td><td></td><td></td><td></td></tr>
                </table>
                <p><a href="/epaper/latest.pdf">Download this week's e-paper</a></p>
                """;
        RecordingClient client = new RecordingClient(Map.of("https://employmentnews.gov.in/newemp/Home.aspx", html));
        SourceCollect.Result collected = SourceCollect.collect(
                spec(
                        "employment_news",
                        "Employment News",
                        "employmentNews",
                        "html_scrape",
                        "",
                        List.of("https://employmentnews.gov.in/newemp/Home.aspx")),
                client,
                "2026-09-30T00:00:00Z");
        assertThat(collected.ok()).isTrue();
        assertThat(collected.records()).hasSize(2);
        assertThat(collected.records())
                .anyMatch(row -> String.valueOf(row.get("title")).toLowerCase().contains("research engineer")
                        && "2026-08-24".equals(row.get("lastDate"))
                        && "https://www.fcriindia.com/recruitment/research-engineer".equals(row.get("officialUrl")));
        assertThat(collected.records())
                .anyMatch(row -> String.valueOf(row.get("title")).toLowerCase().contains("consultant")
                        && "https://employmentnews.gov.in/newemp/Home.aspx".equals(row.get("officialUrl")));
        assertThat(collected.records()).noneMatch(row -> String.valueOf(row.get("officialUrl")).contains(".pdf"));
        assertThat(collected.records()).noneMatch(row -> String.valueOf(row.get("title")).toLowerCase().contains("view more"));
        assertThat(client.pages).isEqualTo(1);
    }

    @Test
    void currentOpeningsHopIsFetchedWhenTheIndexItselfIsNotANotice() {
        String index = "<a href=\"https://example.gov.in/careers/current-openings\">Current Openings</a>";
        String listing = "<a href=\"https://example.gov.in/recruitment/advt-9.pdf\">Recruitment of Junior Engineer advt no 9</a>";
        RecordingClient client = new RecordingClient(Map.of(
                "https://example.gov.in/careers", index,
                "https://example.gov.in/careers/current-openings", listing));
        SourceCollect.Result collected = SourceCollect.collect(
                spec("demo_board", "Demo", "generic", "html_scrape", "", List.of("https://example.gov.in/careers")),
                client,
                "2026-09-30T00:00:00Z");
        assertThat(collected.records())
                .anyMatch(row -> String.valueOf(row.get("title")).contains("Junior Engineer"));
        assertThat(client.pages).isEqualTo(2);
    }

    @Test
    void calendarProseKeepsARegistrationDeadlineWithoutAQualification() {
        String html = """
                <html><head><title>GATE 2027</title></head><body>
                <a href="/notifications">Notifications</a>
                <span class="ticker-item">Regular registration closing date has been extended till 5th Oct 2099.</span>
                </body></html>
                """;
        RecordingClient client = new RecordingClient(Map.of("https://gate2027.iitm.ac.in/", html));
        SourceCollect.Result collected = SourceCollect.collect(
                spec(
                        "gate_calendar",
                        "GATE",
                        "generic",
                        "html_scrape",
                        "",
                        List.of("https://gate2027.iitm.ac.in/")),
                client,
                "2026-09-30T00:00:00Z");
        assertThat(collected.records()).hasSize(1);
        Map<String, Object> row = collected.records().get(0);
        assertThat(row.get("lastDate")).isEqualTo("2099-10-05");
        assertThat(String.valueOf(row.get("officialUrl"))).contains("/notifications");
        assertThat(String.valueOf(row.get("title")).toLowerCase()).contains("registration");
        assertThat(row.get("qualification")).isNull();
        assertThat(client.pages).isEqualTo(1);
    }

    @Test
    void javascriptRedirectOpensTheSameUrlInTheBrowser() {
        SiteClient client = new SiteClient() {
            @Override
            public PageBody page(String url) throws IOException {
                throw new IOException("javascript redirect");
            }

            @Override
            public List<PageBody> browser(List<String> urls) {
                return List.of(new PageBody(
                        urls.get(0),
                        "<a href=\"https://iocl.com/admin/img/UploadedFiles/LatestJobOpening/Files/DetailedAd14082026.pdf\">Click Here to Read the Detailed Advertisement - [14/08/2026]</a>"));
            }
        };
        SourceCollect.Result collected = SourceCollect.collect(
                spec("psu_iocl", "Indian Oil", "generic", "html_scrape", "", List.of("https://iocl.com/latest-job-opening")),
                client,
                "2026-09-30T00:00:00Z");
        assertThat(collected.ok()).isTrue();
        assertThat(collected.records()).anyMatch(row -> String.valueOf(row.get("title")).contains("Detailed Advertisement")
                && String.valueOf(row.get("officialUrl")).startsWith("https://iocl.com/"));

        SiteClient foreign = new SiteClient() {
            @Override
            public PageBody page(String url) throws IOException {
                throw new IOException("redirect left the allowed host");
            }

            @Override
            public List<PageBody> browser(List<String> urls) {
                throw new AssertionError("foreign redirect must not open a browser");
            }
        };
        SourceCollect.Result refused = SourceCollect.collect(
                spec("psu_ntpc", "NTPC", "generic", "html_scrape", "", List.of("https://www.ntpc.co.in/en/careers")),
                foreign,
                "2026-09-30T00:00:00Z");
        assertThat(refused.ok()).isFalse();
        assertThat(refused.message()).contains("allowed host");
        assertThat(client).isNotNull();
    }

    @Test
    void spaShellUsesTheBrowserHtmlWhenTheHttpBodyHasNoAnchors() {
        RecordingClient client = new RecordingClient(Map.of(
                "https://hal-india.co.in/careers", "<html><title>HAL</title><app-root></app-root></html>"));
        client.browserHtml = "<a href=\"https://hal-india.co.in/recruitment/advt-engineer.pdf\">Recruitment of Engineer advt no 12</a>";
        SourceCollect.Result collected = SourceCollect.collect(
                spec("psu_hal", "Hindustan Aeronautics Limited", "generic", "html_scrape", "", List.of("https://hal-india.co.in/careers")),
                client,
                "2026-09-30T00:00:00Z");
        assertThat(collected.records())
                .anyMatch(row -> String.valueOf(row.get("title")).contains("Engineer"));
        assertThat(client.pages).isEqualTo(1);
    }

    private String nodeQuality(Path dir, List<Map<String, String>> samples) throws Exception {
        Path html = dir.resolve("fixture.html");
        Path samplesFile = dir.resolve("samples.json");
        Path script = dir.resolve("compare.js");
        Files.writeString(html, FIXTURE_HTML);
        Files.writeString(samplesFile, mapper.writeValueAsString(samples));
        Files.writeString(
                script,
                """
                const fs = require('fs');
                const { extractLinks } = require(process.argv[2]);
                const { filterJobLinks, isKeepableJobLink } = require(process.argv[3]);
                const { findLastDateHint } = require(process.argv[4]);
                const html = fs.readFileSync(process.argv[5], 'utf8');
                const links = extractLinks(html, 'https://example.gov.in/careers', { jobLikeOnly: true, limit: 20 });
                const hrefs = filterJobLinks(links).map((link) => link.href);
                const samples = JSON.parse(fs.readFileSync(process.argv[6], 'utf8'));
                const dropped = samples.filter((link) => !isKeepableJobLink(link)).map((link) => link.title);
                console.log(JSON.stringify({
                  hrefs,
                  dropped,
                  updated: findLastDateHint('Updated On 20-12-2023'),
                  labeled: findLastDateHint('Last date to apply: 20-12-2023')
                }));
                """);
        Path repo = RepoPaths.root();
        ProcessBuilder builder = new ProcessBuilder(
                "node",
                script.toString(),
                repo.resolve("scripts/collect/lib/htmlLinks.js").toString(),
                repo.resolve("scripts/collect/lib/jobLinkQuality.js").toString(),
                repo.resolve("scripts/collect/lib/dates.js").toString(),
                html.toString(),
                samplesFile.toString());
        builder.directory(repo.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new AssertionError("node link compare exited " + code + "\n" + output);
        }
        return output;
    }

    private static SourceSpec spec(
            String id, String name, String collector, String method, String render, List<String> urls) {
        return new SourceSpec(id, name, collector, method, render, "central", urls, true);
    }

    private static JsonNode source(JsonNode registry, String id) {
        for (JsonNode row : registry.path("sources")) {
            if (id.equals(row.path("sourceId").asText())) {
                return row;
            }
        }
        return null;
    }

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.forEach(item -> out.add(item.isNull() ? null : item.asText()));
        return out;
    }

    private void writeJson(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(40, 700);
                stream.showText(text);
                stream.endText();
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static final class RecordingClient implements SiteClient {
        private final Map<String, String> pagesByUrl;
        private int pages;
        private String browserHtml = "";

        private RecordingClient(Map<String, String> pagesByUrl) {
            this.pagesByUrl = pagesByUrl;
        }

        @Override
        public PageBody page(String url) {
            pages++;
            return new PageBody(url, pagesByUrl.getOrDefault(url, ""));
        }

        @Override
        public List<PageBody> browser(List<String> urls) {
            String url = urls.isEmpty() ? "" : urls.get(0);
            return List.of(new PageBody(url, browserHtml));
        }
    }
}
