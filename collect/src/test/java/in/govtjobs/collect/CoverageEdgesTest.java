package in.govtjobs.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Branches the daily job and the archive need before the coverage gate will pass. */
class CoverageEdgesTest {

    private static final String OPEN_JOB = """
            [{"id":"job-1","title":"Junior Engineer","organization":"Demo Board","orgType":"central","selectionProcess":"cbt","hasExam":true,"officialUrl":"https://example.gov.in/advt","sourceId":"demo_board","sourceName":"Demo","sourceUrl":"https://example.gov.in/careers","status":"open","lastDate":"2099-06-01"}]
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void schemaCheckRejectsAMissingCatalogABadListAndABadSeries(@TempDir Path root) throws Exception {
        SchemaCheck.Report missing = SchemaCheck.check(root);
        assertThat(missing.ok()).isFalse();
        assertThat(missing.message()).contains("Missing data/processed/jobs.json");

        Path processed = root.resolve("data/processed");
        Files.createDirectories(processed);
        Files.writeString(processed.resolve("jobs.json"), "{}");
        SchemaCheck.Report notAList = SchemaCheck.check(root);
        assertThat(notAList.ok()).isFalse();
        assertThat(notAList.message()).contains("jobs.json is not a list");

        Files.writeString(processed.resolve("jobs.json"), OPEN_JOB);
        Files.writeString(processed.resolve("exam_series.json"), "{}");
        SchemaCheck.Report badSeriesFile = SchemaCheck.check(root);
        assertThat(badSeriesFile.ok()).isFalse();
        assertThat(badSeriesFile.message()).contains("exam_series.json is not a list");
        assertThat(badSeriesFile.jobs()).isEqualTo(1);

        Files.writeString(
                processed.resolve("jobs.json"),
                """
                [{"id":"kept-id"}]
                """);
        Files.writeString(
                processed.resolve("exam_series.json"),
                """
                [
                  {"id":"upsc-cse-2026","board":"UPSC","name":"Civil Services Examination 2026","officialUrl":"https://upsc.gov.in/examinations/cse","kind":"series"},
                  {"name":"Nope"}
                ]
                """);
        SchemaCheck.Report badRow = SchemaCheck.check(root);
        assertThat(badRow.ok()).isFalse();
        assertThat(badRow.series()).isEqualTo(2);
        assertThat(badRow.failed()).isGreaterThan(1);
        assertThat(badRow.message()).contains("Schema check failed");
    }

    @Test
    void emptyRegistryDailyDoesNotSnapshot(@TempDir Path root) throws Exception {
        SiteClient offline = url -> {
            throw new IOException("offline");
        };
        DailyPipeline.Result result = DailyPipeline.run(root, offline);
        assertThat(result.exitCode()).isZero();
        assertThat(result.snapshotted()).isFalse();
        assertThat(result.deskMessage()).isEmpty();
        assertThat(Files.isRegularFile(root.resolve("data/processed/collect-report.json"))).isTrue();
    }

    @Test
    void archiveStoresHtmlAndPdfAndIgnoresACacheFailure(@TempDir Path root) throws Exception {
        SiteClient delegate = new SiteClient() {
            @Override
            public PageBody page(String url) {
                if (url.endsWith("/empty")) {
                    return new PageBody(url, null);
                }
                return new PageBody(url, "<p>ok</p>");
            }

            @Override
            public List<PageBody> browser(List<String> urls) {
                if (urls.isEmpty()) {
                    return null;
                }
                return List.of(new PageBody(urls.get(0), "<p>browser</p>"), new PageBody("https://example.com/x", null));
            }

            @Override
            public byte[] bytes(String url) {
                return new byte[] {37, 80, 68, 70};
            }

            @Override
            public boolean documentPdf(String url) {
                return url != null && url.endsWith(".pdf");
            }
        };
        RawArchive archive = new RawArchive(delegate, root, "demo board", "run:1");
        assertThat(archive.page("https://example.com/list").html()).contains("ok");
        archive.page("https://example.com/list");
        archive.page("https://example.com/empty");
        assertThat(archive.browser(List.of())).isNull();
        assertThat(archive.browser(List.of("https://example.com/rendered"))).hasSize(2);
        assertThat(archive.documentPdf("https://example.com/a.pdf")).isTrue();
        assertThat(archive.documentPdf("https://example.com/page")).isFalse();
        assertThat(archive.bytes("https://example.com/a.pdf")).containsExactly(37, 80, 68, 70);
        archive.rememberPdf(new byte[] {37, 80, 68, 70}, "Recruitment notice");
        archive.rememberPdf(new byte[] {9, 9, 9}, "  ");

        Path htmlDir = root.resolve("data/raw/demo_board");
        try (var walk = Files.walk(htmlDir)) {
            assertThat(walk.filter(path -> path.getFileName().toString().endsWith(".html")).count()).isEqualTo(2);
        }
        String hash = PdfDocuments.sha256(new byte[] {37, 80, 68, 70});
        assertThat(Files.readString(root.resolve("data/raw/pdfs").resolve(hash + ".txt"))).isEqualTo("Recruitment notice");

        Path blocked = Files.createFile(root.resolve("not-a-directory"));
        RawArchive broken = new RawArchive(delegate, blocked, null, null);
        assertThat(broken.page("https://example.com/list").html()).contains("ok");
        assertThat(broken.bytes("https://example.com/a.pdf")).containsExactly(37, 80, 68, 70);
        broken.rememberPdf(new byte[] {1, 2, 3}, "text");

        byte[] cached = new byte[] {1, 2, 3, 4};
        PdfDocuments.storeText(root, cached, "first");
        PdfDocuments.storeText(root, cached, "second");
        PdfDocuments.storeText(root, cached, "");
        assertThat(Files.readString(root.resolve("data/raw/pdfs").resolve(PdfDocuments.sha256(cached) + ".txt")))
                .isEqualTo("first");
        assertThat(PdfDocuments.text(new byte[0])).isEmpty();
        assertThat(PdfDocuments.text(null)).isEmpty();
    }

    @Test
    void progressCapsTheLogAndSurvivesAWriteFailure(@TempDir Path root) throws Exception {
        Path blocked = root.resolve("blocked");
        Files.writeString(blocked, "x");
        CollectProgress unwritable = new CollectProgress(blocked, "run", "2026-10-10T00:00:00Z");
        unwritable.start(1);

        CollectProgress progress = new CollectProgress(root, "run", "2026-10-10T00:00:00Z");
        progress.start(51);
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < 251; i++) {
            errors.add("finding-" + i);
        }
        progress.finished(new CollectOrchestrator.SourceOutcome("s0", SourceRunKind.FAILED, true, -1, "m", errors));
        for (int i = 1; i <= 50; i++) {
            progress.finished(
                    new CollectOrchestrator.SourceOutcome("s" + i, SourceRunKind.EMPTY, true, 0, null, List.of()));
        }
        progress.finish();
        JsonNode report = mapper.readTree(root.resolve("data/processed/collect-progress.json").toFile());
        assertThat(report.path("findings")).hasSize(250);
        assertThat(report.path("sourcesLog")).hasSize(50);
        assertThat(report.path("running").asBoolean()).isFalse();
        assertThat(report.path("sourcesLog").get(0).path("sourceId").asText()).isEqualTo("s1");
    }

    @Test
    void defaultsArchiveClientAndSnapshotGuard(@TempDir Path root) throws Exception {
        SiteClient client = url -> new SiteClient.PageBody(url, "<p>page</p>");
        assertThat(client.browser(List.of("https://example.com/a"))).hasSize(1);
        assertThat(client.documentPdf("https://example.com/notice.pdf?x=1")).isTrue();
        assertThat(client.documentPdf("https://example.com/careers")).isFalse();
        assertThatThrownBy(() -> client.bytes("https://example.com/a.pdf"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("pdf download not available");

        LiveSiteClient live = new LiveSiteClient();
        assertThat(live.documentPdf("https://example.com/notice.PDF")).isTrue();
        assertThat(live.documentPdf("https://example.com/careers")).isFalse();
        assertThat(live.documentPdf(null)).isFalse();
        assertThatThrownBy(() -> live.page("http://example.com/list"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("non-https");
        assertThatThrownBy(() -> live.bytes("http://example.com/a.pdf"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("non-https");

        SourceSpec spec = SourceSpec.from(Map.of(
                "sourceId", "demo",
                "baseUrl", "https://example.gov.in/careers",
                "listUrls", List.of("https://example.gov.in/a", " ")));
        assertThat(spec.listUrls()).containsExactly("https://example.gov.in/a", "https://example.gov.in/careers");

        Files.writeString(root.resolve(".env"), "NO_EQUALS\n=missing\nOK_KEY=value\n");
        Map<String, String> env = DailyEnvironment.load(root);
        assertThat(env.get("OK_KEY")).isEqualTo("value");
        assertThat(env).doesNotContainKey("NO_EQUALS");

        assertThatThrownBy(() -> SnapshotPrev.copy(root))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Missing data/processed/jobs.json");
    }

    @Test
    void alertSkipsCorruptCatalogsAndShowsALastDate(@TempDir Path root) throws Exception {
        Map<String, String> env = mailbox();
        Files.createDirectories(root.resolve("data/processed"));
        Files.writeString(root.resolve("data/processed/jobs.prev.json"), "[]");
        AlertMail.Report missing = AlertMail.send(root, env, mail -> {
            throw new AssertionError("mail");
        });
        assertThat(missing.reason()).isEqualTo("no_new_jobs");

        Files.writeString(root.resolve("data/processed/jobs.json"), "{}");
        assertThat(AlertMail.send(root, env, mail -> {
                    throw new AssertionError("mail");
                }).reason())
                .isEqualTo("no_new_jobs");

        Files.writeString(root.resolve("data/processed/jobs.json"), "{");
        assertThat(AlertMail.send(root, env, mail -> {
                    throw new AssertionError("mail");
                }).reason())
                .isEqualTo("no_new_jobs");

        Files.writeString(root.resolve("data/processed/jobs.json"), OPEN_JOB);
        env.put("SMTP_PORT", "nope");
        List<AlertMail.Mail> sent = new ArrayList<>();
        AlertMail.Report mailed = AlertMail.send(root, env, sent::add);
        assertThat(mailed.sent()).isTrue();
        assertThat(sent.get(0).port()).isEqualTo(587);
        assertThat(sent.get(0).text()).contains("2099-06-01");
        assertThat(sent.get(0).html()).contains("2099-06-01");

        env.put("SMTP_PORT", "  ");
        sent.clear();
        AlertMail.send(root, env, sent::add);
        assertThat(sent.get(0).port()).isEqualTo(587);
    }

    @Test
    void smtpSessionAcceptsLoginAndABlankPassword() throws Exception {
        try (SmtpTrap trap = new SmtpTrap()) {
            AlertMail.smtpMailer()
                    .send(mail(trap.port(), "bot", null, null, null));
        }
        try (SmtpTrap trap = new SmtpTrap()) {
            AlertMail.smtpMailer()
                    .send(mail(trap.port(), "bot", "secret", "plain text", "<p>html</p>"));
        }
    }

    private static AlertMail.Mail mail(int port, String user, String password, String text, String html) {
        return new AlertMail.Mail(
                "127.0.0.1", port, false, user, password, "bot@example.com", "ops@example.com", "subject", text, html);
    }

    private static Map<String, String> mailbox() {
        return new java.util.LinkedHashMap<>(Map.of(
                "ALERT_ENABLED", "true",
                "SMTP_HOST", "127.0.0.1",
                "ALERT_TO", "ops@example.com",
                "SMTP_FROM", "bot@example.com"));
    }

    /** Speaks enough SMTP for {@link SmtpMailer} to finish Transport.send. */
    private static final class SmtpTrap implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;
        private final StringBuilder transcript = new StringBuilder();
        private volatile Exception failure;

        SmtpTrap() throws IOException {
            server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
            thread = new Thread(this::serve, "smtp-trap");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        private void serve() {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(8_000);
                    talk(socket);
                } catch (IOException ex) {
                    if (!server.isClosed()) {
                        failure = ex;
                    }
                    break;
                }
            }
        }

        private void talk(Socket socket) throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            reply(out, "220 localhost ready");
            String mode = "command";
            String line;
            while ((line = in.readLine()) != null) {
                transcript.append(line).append('\n');
                if ("data".equals(mode)) {
                    if (".".equals(line)) {
                        reply(out, "250 queued");
                        mode = "command";
                    }
                    continue;
                }
                if ("user".equals(mode)) {
                    reply(out, "334 UGFzc3dvcmQ6");
                    mode = "pass";
                    continue;
                }
                if ("pass".equals(mode)) {
                    reply(out, "235 ok");
                    mode = "command";
                    continue;
                }
                String upper = line.toUpperCase(Locale.ROOT);
                if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                    reply(out, "250-localhost");
                    reply(out, "250 AUTH LOGIN");
                } else if (upper.startsWith("AUTH LOGIN")) {
                    reply(out, "334 VXNlcm5hbWU6");
                    mode = "user";
                } else if (upper.startsWith("AUTH PLAIN ")) {
                    reply(out, "235 ok");
                } else if (upper.equals("AUTH PLAIN")) {
                    reply(out, "334");
                    mode = "pass";
                } else if (upper.startsWith("MAIL") || upper.startsWith("RCPT") || upper.startsWith("RSET") || upper.startsWith("NOOP")) {
                    reply(out, "250 ok");
                } else if (upper.startsWith("DATA")) {
                    reply(out, "354 send");
                    mode = "data";
                } else if (upper.startsWith("QUIT")) {
                    reply(out, "221 bye");
                    break;
                } else {
                    reply(out, "502 unsupported");
                }
            }
        }

        private static void reply(BufferedWriter out, String line) throws IOException {
            out.write(line);
            out.write("\r\n");
            out.flush();
        }

        @Override
        public void close() throws IOException {
            server.close();
            try {
                thread.join(3_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (failure != null) {
                throw new IOException(transcript.toString(), failure);
            }
        }
    }
}
