package in.govtjobs.web.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.store.JobStore;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectQueueMoreTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsFiltersPatchesAndStatesWithoutFetching(@TempDir Path root) throws Exception {
        withRoot(root, () -> {
            OfficialUrlPolicy urls = allowAny();
            JobStore jobs = new JobStore(mapper);
            GovtJobsProperties props = new GovtJobsProperties();
            CollectQueue queue = new CollectQueue(mapper, urls, props, jobs);

            assertThat(queue.list(null)).isEmpty();
            assertThat(queue.progress()).containsEntry("running", false).containsEntry("phase", "idle");

            Path processed = root.resolve("data").resolve("processed");
            Files.createDirectories(processed);
            Files.writeString(processed.resolve("collect-progress.json"), "{\"running\":true,\"phase\":\"fetch\"}");
            assertThat(queue.progress()).containsEntry("running", true).containsEntry("phase", "fetch");
            Files.writeString(processed.resolve("collect-progress.json"), "not-json");
            assertThat(queue.progress()).containsEntry("phase", "idle");

            Map<String, Object> kept = job("job-1", "needs_review", mapOf("title", "Clerk", "officialUrl", "https://ssc.gov.in/n"));
            Map<String, Object> other = job("job-2", "held", "not-a-map");
            other.put("timeline", "nope");
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("jobs", List.of(kept, other));
            mapper.writeValue(processed.resolve("collect-jobs.json").toFile(), doc);

            assertThat(queue.list("")).hasSize(2);
            assertThat(queue.list("needs_review")).extracting(j -> j.get("id")).containsExactly("job-1");
            assertThat(queue.list("missing")).isEmpty();
            assertThat(queue.get("job-1")).isNotNull();
            assertThat(queue.get("nope")).isNull();
            assertThat(queue.patchExtracted("missing", Map.of("title", "x"))).isNull();

            Map<String, Object> patched = queue.patchExtracted("job-2", Map.of("title", "Edited", "summary", "note"));
            assertThat(patched.get("extracted")).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) patched.get("extracted")).get("title")).isEqualTo("Edited");

            Map<String, Object> merged = queue.patchExtracted("job-1", Map.of("summary", "added"));
            assertThat(((Map<?, ?>) merged.get("extracted")).get("title")).isEqualTo("Clerk");
            assertThat(((Map<?, ?>) merged.get("extracted")).get("summary")).isEqualTo("added");

            Map<String, Object> held = queue.setState("job-2", "held", null);
            assertThat(held.get("state")).isEqualTo("held");
            assertThat(held.containsKey("reason")).isFalse();
            assertThat(held.get("timeline")).isInstanceOf(List.class);

            Map<String, Object> rejected = queue.setState("job-1", "rejected", "duplicate");
            assertThat(rejected.get("reason")).isEqualTo("duplicate");
            assertThat(queue.setState("missing", "held", "x")).isNull();

            Files.writeString(processed.resolve("collect-jobs.json"), "{");
            assertThatThrownBy(() -> queue.list(null)).isInstanceOf(IllegalStateException.class);

            mapper.writeValue(processed.resolve("collect-jobs.json").toFile(), Map.of());
            assertThat(queue.list(null)).isEmpty();
        });
    }

    @Test
    void publishAndUnpublishStayOnTheLocalStore(@TempDir Path root) {
        withRoot(root, () -> {
            OfficialUrlPolicy urls = allowAny();
            JobStore jobs = new JobStore(mapper);
            CollectQueue queue = new CollectQueue(mapper, urls, new GovtJobsProperties(), jobs);
            Map<String, Object> row = job(
                    "pub-1",
                    "needs_review",
                    mapOf(
                            "organization", "SSC",
                            "title", "Clerk",
                            "lastDate", "2099-01-01",
                            "officialUrl", "https://ssc.gov.in/notice",
                            "hasExam", Boolean.TRUE,
                            "orgType", "central",
                            "summary", "Check the notice"));
            row.put("host", "ssc.gov.in");
            row.put("url", "https://ssc.gov.in/notice");
            row.put("sourceLabel", "Ops paste");
            writeJobs(root, List.of(row));

            Map<String, Object> published = queue.setState("pub-1", "published", "ok");
            assertThat(published.get("state")).isEqualTo("published");
            assertThat(published.get("opportunityId")).isNotNull();
            String id = String.valueOf(published.get("opportunityId"));
            assertThat(jobs.getJobById(id)).isNotNull();
            assertThat(jobs.getJobById(id).get("hasExam")).isEqualTo(true);

            Map<String, Object> bare = job("pub-2", "needs_review", mapOf("lastDate", "2099-06-01"));
            bare.put("host", "ssc.gov.in");
            bare.put("url", "https://ssc.gov.in/other");
            writeJobs(root, List.of(bare));
            Map<String, Object> local = queue.setState("pub-2", "published_local", "local");
            assertThat(local.get("opportunityId")).isNotNull();
            Map<String, Object> pasted = jobs.getJobById(String.valueOf(local.get("opportunityId")));
            assertThat(pasted).isNotNull();
            assertThat(pasted.get("title")).isEqualTo("Pasted opportunity");

            Map<String, Object> unpublished = queue.setState("pub-2", "unpublished", "pull");
            assertThat(unpublished.get("state")).isEqualTo("unpublished");
            assertThat(jobs.getJobById(String.valueOf(local.get("opportunityId")))).isNull();

            Map<String, Object> never = job("pub-3", "needs_review", Map.of());
            writeJobs(root, List.of(never));
            assertThat(queue.setState("pub-3", "unpublished", "none").get("state")).isEqualTo("unpublished");
        });
    }

    @Test
    void enqueueReadsALocalPageAndSurvivesARefusedPort(@TempDir Path root) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] titled = "<html><title>  Hello   World </title><p>ok</p></html>".getBytes(StandardCharsets.UTF_8);
        byte[] longBody = new byte[4000];
        java.util.Arrays.fill(longBody, (byte) 'x');
        System.arraycopy("<title>Late</title>".getBytes(StandardCharsets.UTF_8), 0, longBody, 3000, 19);
        byte[] plain = "no title here".getBytes(StandardCharsets.UTF_8);
        server.createContext("/titled", exchange -> respond(exchange, titled));
        server.createContext("/long", exchange -> respond(exchange, longBody));
        server.createContext("/plain", exchange -> respond(exchange, plain));
        server.start();
        try {
            withRoot(root, () -> {
                int port = server.getAddress().getPort();
                OfficialUrlPolicy urls = allowAny();
                GovtJobsProperties props = new GovtJobsProperties();
                props.getOps().setConnectTimeoutSeconds(0);
                props.getOps().setRequestTimeoutSeconds(0);
                props.getOps().setUserAgent("  ");
                props.getOps().setMaxBodyBytes(1024);
                CollectQueue queue = new CollectQueue(mapper, urls, props, new JobStore(mapper));

                Map<String, Object> titledJob = queue.enqueue("http://127.0.0.1:" + port + "/titled", "label");
                assertThat(titledJob.get("state")).isEqualTo("needs_review");
                assertThat(titledJob.get("host")).isEqualTo("127.0.0.1");
                Map<?, ?> extracted = (Map<?, ?>) titledJob.get("extracted");
                assertThat(extracted.get("title")).isEqualTo("Hello World");
                assertThat(extracted.get("httpStatus")).isEqualTo(200);

                props.getOps().setUserAgent("UnitBot");
                props.getOps().setMaxBodyBytes(1500);
                Map<?, ?> truncated = (Map<?, ?>) queue.enqueue("http://127.0.0.1:" + port + "/long", null).get("extracted");
                assertThat(String.valueOf(truncated.get("title"))).startsWith("http://127.0.0.1");

                Map<?, ?> plainExtracted =
                        (Map<?, ?>) queue.enqueue("http://127.0.0.1:" + port + "/plain", "p").get("extracted");
                assertThat(String.valueOf(plainExtracted.get("title"))).contains("/plain");

                Map<?, ?> failed = (Map<?, ?>) queue.enqueue("http://127.0.0.1:1/nope", "down").get("extracted");
                assertThat(failed.get("summary")).isEqualTo("Could not fetch page — edit facts before publish.");
                assertThat(queue.list("needs_review").size()).isGreaterThanOrEqualTo(4);
            });
        } finally {
            server.stop(0);
        }
    }

    private static OfficialUrlPolicy allowAny() {
        OfficialUrlPolicy urls = mock(OfficialUrlPolicy.class);
        when(urls.requireAllowed(anyString())).thenAnswer(inv -> URI.create(inv.getArgument(0)));
        return urls;
    }

    private void writeJobs(Path root, List<Map<String, Object>> jobs) {
        try {
            Path file = root.resolve("data").resolve("processed").resolve("collect-jobs.json");
            Files.createDirectories(file.getParent());
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("jobs", jobs);
            mapper.writeValue(file.toFile(), doc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> job(String id, String state, Object extracted) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("state", state);
        row.put("extracted", extracted);
        row.put("timeline", new ArrayList<Map<String, Object>>());
        return row;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws java.io.IOException {
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @FunctionalInterface
    private interface Work {
        void run() throws Exception;
    }

    private static void withRoot(Path root, Work action) {
        String previous = System.getProperty("user.dir");
        try {
            Files.createDirectories(root.resolve("data"));
            Files.writeString(root.resolve("pom.xml"), "<project/>");
            System.setProperty("user.dir", root.toString());
            assertThat(RepoPaths.root()).isEqualTo(root.toAbsolutePath().normalize());
            action.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            System.setProperty("user.dir", previous);
        }
    }
}
