package in.govtjobs.web.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.domain.RepoPaths;
import in.govtjobs.domain.job.JobSchema;
import in.govtjobs.web.config.FeatureFlags;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.security.SessionCookies;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.OperatorStore;
import in.govtjobs.web.store.StoreException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class OpsPublishTest {

    private static final String LAST_DATE_REQUIRED =
            "Last date must be YYYY-MM-DD before this can be published. A missing date is not published as open.";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publishUsesTheFactsSubmittedOnTheForm(@TempDir Path root) {
        withRoot(root, () -> {
            Harness harness = harness();
            writeJobs(root, List.of(reviewItem("review-1")));

            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            String view = publish(
                    harness,
                    "review-1",
                    "Junior Engineer",
                    "Staff Selection Commission",
                    "https://ssc.gov.in/new-notice",
                    "2099-08-20",
                    "walk_in",
                    "Walk-in at the regional office",
                    "on",
                    redirect);

            assertThat(view).isEqualTo("redirect:/ops/runs/review-1");
            assertThat(redirect.getFlashAttributes()).isEmpty();
            Map<String, Object> job = harness.queue.get("review-1");
            assertThat(job.get("state")).isEqualTo("published_local");
            assertThat(extracted(job))
                    .containsEntry("title", "Junior Engineer")
                    .containsEntry("organization", "Staff Selection Commission")
                    .containsEntry("officialUrl", "https://ssc.gov.in/new-notice")
                    .containsEntry("summary", "Walk-in at the regional office")
                    .containsEntry("selectionProcess", "walk_in")
                    .containsEntry("hasExam", true)
                    .containsEntry("lastDate", "2099-08-20");

            String id = String.valueOf(job.get("opportunityId"));
            Map<String, Object> row = harness.jobs.getJobById(id);
            assertThat(row)
                    .containsEntry("title", "Junior Engineer")
                    .containsEntry("organization", "Staff Selection Commission")
                    .containsEntry("officialUrl", "https://ssc.gov.in/new-notice")
                    .containsEntry("summary", "Walk-in at the regional office")
                    .containsEntry("selectionProcess", "walk_in")
                    .containsEntry("hasExam", true)
                    .containsEntry("lastDate", "2099-08-20")
                    .containsEntry("status", JobSchema.computeStatus("2099-08-20"));
            assertThat(row.get("status")).isNotEqualTo("closed");

            publish(
                    harness,
                    "review-1",
                    "Junior Engineer revised",
                    "Staff Selection Commission",
                    "https://ssc.gov.in/revised",
                    "2099-09-01",
                    "cbt",
                    "Revised summary",
                    null,
                    new RedirectAttributesModelMap());
            Map<String, Object> republished = harness.queue.get("review-1");
            String revisedId = String.valueOf(republished.get("opportunityId"));
            assertThat(revisedId).isNotEqualTo(id);
            assertThat(harness.jobs.getJobById(id)).isNull();
            assertThat(extracted(republished))
                    .containsEntry("title", "Junior Engineer revised")
                    .containsEntry("officialUrl", "https://ssc.gov.in/revised")
                    .containsEntry("lastDate", "2099-09-01")
                    .containsEntry("selectionProcess", "cbt")
                    .containsEntry("summary", "Revised summary")
                    .containsEntry("hasExam", false);
            assertThat(harness.jobs.getJobById(revisedId))
                    .containsEntry("title", "Junior Engineer revised")
                    .containsEntry("organization", "Staff Selection Commission")
                    .containsEntry("officialUrl", "https://ssc.gov.in/revised")
                    .containsEntry("lastDate", "2099-09-01")
                    .containsEntry("selectionProcess", "cbt")
                    .containsEntry("summary", "Revised summary")
                    .containsEntry("hasExam", false)
                    .containsEntry("status", JobSchema.computeStatus("2099-09-01"));
            assertThat(harness.jobs.getJobs()).hasSize(1);
        });
    }

    @Test
    void pastLastDatePublishesAsClosed(@TempDir Path root) {
        withRoot(root, () -> {
            Harness harness = harness();
            writeJobs(root, List.of(reviewItem("past-1")));
            publish(
                    harness,
                    "past-1",
                    "Closed walk-in",
                    "SSC",
                    "https://ssc.gov.in/closed",
                    "2020-01-15",
                    "walk_in",
                    "Already over",
                    "on",
                    new RedirectAttributesModelMap());
            Map<String, Object> row = harness.jobs.getJobById(
                    String.valueOf(harness.queue.get("past-1").get("opportunityId")));
            assertThat(row).containsEntry("lastDate", "2020-01-15").containsEntry("status", "closed");
            assertThat(harness.jobs.getJobs()).noneMatch(item -> "open".equals(item.get("status")));
        });
    }

    @Test
    void blankOrUnparseableLastDateIsNotPublishedAsOpen(@TempDir Path root) {
        withRoot(root, () -> {
            assertThat(CollectQueue.LAST_DATE_REQUIRED).isEqualTo(LAST_DATE_REQUIRED);
            assertThat(JobSchema.computeStatus("")).isEqualTo("open");
            assertThat(JobSchema.computeStatus("soon")).isEqualTo("open");

            Harness harness = harness();
            Map<String, Object> undated = reviewItem("direct-1");
            extracted(undated).put("lastDate", "");
            writeJobs(root, List.of(reviewItem("blank-1"), undated));

            assertThatThrownBy(() -> harness.queue.setState("direct-1", "published_local", "local"))
                    .isInstanceOf(StoreException.class)
                    .hasMessage(LAST_DATE_REQUIRED);
            assertThat(harness.queue.get("direct-1").get("state")).isEqualTo("needs_review");

            for (String lastDate : List.of("", "   ", "soon", "null")) {
                RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
                String view = publish(
                        harness,
                        "blank-1",
                        "False open",
                        "Other Org",
                        "https://ssc.gov.in/false",
                        lastDate,
                        "cbt",
                        "should not publish",
                        "on",
                        redirect);
                assertThat(view).isEqualTo("redirect:/ops/runs/blank-1");
                assertThat(redirect.getFlashAttributes().get("notice")).isEqualTo(LAST_DATE_REQUIRED);
                Map<String, Object> job = harness.queue.get("blank-1");
                assertThat(job.get("state")).isEqualTo("needs_review");
                assertThat(job.get("opportunityId")).isNull();
                assertThat(extracted(job))
                        .containsEntry("title", "Stored title")
                        .containsEntry("lastDate", "2099-01-01")
                        .containsEntry("officialUrl", "https://ssc.gov.in/old");
            }
            assertThat(Files.exists(catalogFile(root))).isFalse();
            assertThat(harness.jobs.getJobs()).isEmpty();

            publish(
                    harness,
                    "blank-1",
                    "Real title",
                    "SSC",
                    "https://ssc.gov.in/real",
                    "2099-08-20",
                    "walk_in",
                    "kept",
                    "on",
                    new RedirectAttributesModelMap());
            String keptId = String.valueOf(harness.queue.get("blank-1").get("opportunityId"));
            assertThat(harness.jobs.getJobById(keptId))
                    .containsEntry("title", "Real title")
                    .containsEntry("lastDate", "2099-08-20")
                    .containsEntry("status", JobSchema.computeStatus("2099-08-20"));

            RedirectAttributesModelMap blocked = new RedirectAttributesModelMap();
            publish(
                    harness,
                    "blank-1",
                    "Replacement",
                    "Other",
                    "https://ssc.gov.in/replacement",
                    "",
                    "cbt",
                    "nope",
                    null,
                    blocked);
            assertThat(blocked.getFlashAttributes().get("notice")).isEqualTo(LAST_DATE_REQUIRED);
            Map<String, Object> job = harness.queue.get("blank-1");
            assertThat(job.get("state")).isEqualTo("published_local");
            assertThat(job.get("opportunityId")).isEqualTo(keptId);
            assertThat(extracted(job)).containsEntry("title", "Real title").containsEntry("lastDate", "2099-08-20");
            assertThat(harness.jobs.getJobs()).hasSize(1);
            assertThat(harness.jobs.getJobById(keptId))
                    .containsEntry("title", "Real title")
                    .containsEntry("officialUrl", "https://ssc.gov.in/real")
                    .containsEntry("status", JobSchema.computeStatus("2099-08-20"));
            String catalog = Files.readString(catalogFile(root));
            assertThat(catalog).contains("Real title").doesNotContain("Replacement").doesNotContain("False open");
        });
    }

    @Test
    void disallowedUrlStaysReviewableWithoutACatalogRow(@TempDir Path root) {
        withRoot(root, () -> {
            Harness harness = harness();
            writeJobs(root, List.of(reviewItem("url-1")));
            Map<String, String> failures = new LinkedHashMap<>();
            failures.put("http://ssc.gov.in/notice", "Only https official URLs are accepted");
            failures.put("https://example.com/notice", "Host must be a .gov.in or .nic.in official site");
            for (Map.Entry<String, String> failure : failures.entrySet()) {
                RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
                publish(
                        harness,
                        "url-1",
                        "Blocked",
                        "SSC",
                        failure.getKey(),
                        "2099-08-20",
                        "walk_in",
                        "nope",
                        "on",
                        redirect);
                assertThat(redirect.getFlashAttributes().get("notice")).isEqualTo(failure.getValue());
                Map<String, Object> job = harness.queue.get("url-1");
                assertThat(job.get("state")).isEqualTo("needs_review");
                assertThat(job.get("opportunityId")).isNull();
                assertThat(extracted(job))
                        .containsEntry("title", "Stored title")
                        .containsEntry("officialUrl", "https://ssc.gov.in/old");
            }
            assertThat(harness.jobs.getJobs()).isEmpty();
            assertThat(Files.exists(catalogFile(root))).isFalse();
        });
    }

    @Test
    void saveRejectAndUnpublishDoNotApplyThePublishForm(@TempDir Path root) {
        withRoot(root, () -> {
            Harness harness = harness();
            writeJobs(root, List.of(reviewItem("edit-1"), reviewItem("live-1")));

            RedirectAttributesModelMap saved = new RedirectAttributesModelMap();
            String saveView = harness.controller.runAction(
                    "edit-1",
                    "save",
                    "Saved title",
                    "Saved org",
                    "https://ssc.gov.in/saved",
                    "",
                    "walk_in",
                    "Saved summary",
                    "on",
                    "not a reason",
                    saved);
            assertThat(saveView).isEqualTo("redirect:/ops/runs/edit-1");
            assertThat(saved.getFlashAttributes()).isEmpty();
            Map<String, Object> afterSave = harness.queue.get("edit-1");
            assertThat(afterSave.get("state")).isEqualTo("needs_review");
            assertThat(extracted(afterSave))
                    .containsEntry("title", "Saved title")
                    .containsEntry("organization", "Saved org")
                    .containsEntry("officialUrl", "https://ssc.gov.in/saved")
                    .containsEntry("lastDate", "")
                    .containsEntry("selectionProcess", "walk_in")
                    .containsEntry("summary", "Saved summary")
                    .containsEntry("hasExam", true);
            assertThat(harness.jobs.getJobs()).isEmpty();

            RedirectAttributesModelMap rejected = new RedirectAttributesModelMap();
            harness.controller.runAction(
                    "edit-1",
                    "reject",
                    "Rejected title",
                    "Rejected org",
                    "https://example.com/reject",
                    "2099-08-20",
                    "cbt",
                    "Rejected summary",
                    "on",
                    "duplicate notice",
                    rejected);
            assertThat(rejected.getFlashAttributes()).isEmpty();
            Map<String, Object> afterReject = harness.queue.get("edit-1");
            assertThat(afterReject.get("state")).isEqualTo("rejected");
            assertThat(afterReject.get("reason")).isEqualTo("duplicate notice");
            assertThat(extracted(afterReject))
                    .containsEntry("title", "Saved title")
                    .containsEntry("officialUrl", "https://ssc.gov.in/saved")
                    .containsEntry("lastDate", "");
            assertThat(harness.jobs.getJobs()).isEmpty();

            publish(
                    harness,
                    "live-1",
                    "Live title",
                    "Live org",
                    "https://ssc.gov.in/live",
                    "2099-08-20",
                    "walk_in",
                    "Live summary",
                    "true",
                    new RedirectAttributesModelMap());
            assertThat(harness.jobs.getJobs()).hasSize(1);

            RedirectAttributesModelMap unpublished = new RedirectAttributesModelMap();
            harness.controller.runAction(
                    "live-1",
                    "unpublish",
                    "Should not apply",
                    "Other org",
                    "https://example.com/nope",
                    "",
                    "cbt",
                    "nope",
                    "on",
                    "pull it",
                    unpublished);
            assertThat(unpublished.getFlashAttributes()).isEmpty();
            Map<String, Object> afterUnpublish = harness.queue.get("live-1");
            assertThat(afterUnpublish.get("state")).isEqualTo("unpublished");
            assertThat(afterUnpublish.get("reason")).isEqualTo("Unpublished");
            assertThat(extracted(afterUnpublish))
                    .containsEntry("title", "Live title")
                    .containsEntry("organization", "Live org")
                    .containsEntry("officialUrl", "https://ssc.gov.in/live")
                    .containsEntry("lastDate", "2099-08-20")
                    .containsEntry("selectionProcess", "walk_in")
                    .containsEntry("summary", "Live summary")
                    .containsEntry("hasExam", true);
            assertThat(harness.jobs.getJobs()).isEmpty();
            assertThat(harness.queue.get("edit-1").get("state")).isEqualTo("rejected");
        });
    }

    private static String publish(
            Harness harness,
            String id,
            String title,
            String organization,
            String officialUrl,
            String lastDate,
            String selectionProcess,
            String summary,
            String hasExam,
            RedirectAttributesModelMap redirect) {
        return harness.controller.runAction(
                id,
                "publish",
                title,
                organization,
                officialUrl,
                lastDate,
                selectionProcess,
                summary,
                hasExam,
                "ignore this reason",
                redirect);
    }

    private Harness harness() {
        JobStore jobs = new JobStore(mapper);
        CollectQueue queue = new CollectQueue(mapper, urls(), new GovtJobsProperties(), jobs);
        OpsController controller = new OpsController(
                queue,
                jobs,
                mock(OperatorStore.class),
                new OpsLog(),
                new FeatureFlags(),
                mock(SessionCookies.class));
        return new Harness(queue, jobs, controller);
    }

    private static OfficialUrlPolicy urls() {
        OfficialUrlPolicy real = new OfficialUrlPolicy(new GovtJobsProperties());
        OfficialUrlPolicy urls = mock(OfficialUrlPolicy.class);
        when(urls.requireAllowed(anyString())).thenAnswer(inv -> {
            String raw = inv.getArgument(0);
            URI uri = real.requireHttps(raw);
            if (!real.isAllowedHost(uri.getHost())) {
                throw new IllegalArgumentException("Host must be a .gov.in or .nic.in official site");
            }
            return uri;
        });
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

    private static Path catalogFile(Path root) {
        return root.resolve("data").resolve("processed").resolve("jobs.json");
    }

    private static Map<String, Object> reviewItem(String id) {
        Map<String, Object> extracted = new LinkedHashMap<>();
        extracted.put("title", "Stored title");
        extracted.put("organization", "Stored Org");
        extracted.put("officialUrl", "https://ssc.gov.in/old");
        extracted.put("lastDate", "2099-01-01");
        extracted.put("selectionProcess", "direct_recruitment");
        extracted.put("summary", "Stored summary");
        extracted.put("hasExam", false);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("state", "needs_review");
        row.put("host", "ssc.gov.in");
        row.put("url", "https://ssc.gov.in/old");
        row.put("sourceLabel", "Ops paste");
        row.put("extracted", extracted);
        row.put("timeline", new ArrayList<Map<String, Object>>());
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extracted(Map<String, Object> job) {
        return (Map<String, Object>) job.get("extracted");
    }

    private record Harness(CollectQueue queue, JobStore jobs, OpsController controller) {}

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
