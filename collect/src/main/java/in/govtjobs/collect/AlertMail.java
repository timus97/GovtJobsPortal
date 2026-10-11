package in.govtjobs.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diff jobs.json against jobs.prev.json and email at most forty new open jobs.
 * A disabled alert or a missing mailbox is not a send failure. A transport error is.
 */
final class AlertMail {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST = new TypeReference<>() {};

    private AlertMail() {}

    /** Thrown failures skip the snapshot and make the daily job exit 1. */
    @FunctionalInterface
    interface Mailer {
        void send(Mail mail) throws Exception;
    }

    record Mail(
            String host,
            int port,
            boolean secure,
            String user,
            String password,
            String from,
            String to,
            String subject,
            String text,
            String html) {}

    record Report(boolean sent, boolean failed, String reason, int newCount) {
        boolean ok() {
            return !failed;
        }
    }

    static Mailer smtpMailer() {
        return new SmtpMailer();
    }

    static Report send(Path root, Map<String, String> env, Mailer mailer) throws IOException {
        Map<String, String> values = env == null ? Map.of() : env;
        String now = Instant.now().toString();
        boolean enabled = "true".equals(values.get("ALERT_ENABLED"));
        boolean onFirst = "true".equals(values.get("ALERT_ON_FIRST_RUN"));
        List<Map<String, Object>> jobs = readJobs(root.resolve("data").resolve("processed").resolve("jobs.json"));
        Path prevPath = root.resolve("data").resolve("processed").resolve("jobs.prev.json");
        List<Map<String, Object>> prev = Files.isRegularFile(prevPath) ? readJobs(prevPath) : null;
        if (!enabled) {
            System.out.println("Alerts disabled (set ALERT_ENABLED=true to send).");
            return write(root, now, false, false, "ALERT_ENABLED not true", 0, null, null);
        }
        String host = values.get("SMTP_HOST");
        String to = values.get("ALERT_TO");
        if (host == null || host.isBlank() || to == null || to.isBlank()) {
            System.out.println("Missing SMTP_HOST or ALERT_TO — skip send.");
            return write(root, now, false, false, "Missing SMTP_HOST or ALERT_TO", 0, null, null);
        }
        if (prev == null && !onFirst) {
            System.out.println("No previous snapshot — skip first-run spam. Run snapshot after this.");
            return write(
                    root,
                    now,
                    false,
                    false,
                    "No jobs.prev.json (set ALERT_ON_FIRST_RUN=true to alert on first run)",
                    0,
                    null,
                    null);
        }
        Set<String> prevIds = new LinkedHashSet<>();
        if (prev != null) {
            for (Map<String, Object> job : prev) {
                if (job.get("id") != null) {
                    prevIds.add(String.valueOf(job.get("id")));
                }
            }
        }
        List<Map<String, Object>> fresh = new ArrayList<>();
        for (Map<String, Object> job : jobs) {
            String id = job.get("id") == null ? "" : String.valueOf(job.get("id"));
            String status = job.get("status") == null ? "" : String.valueOf(job.get("status"));
            if (!prevIds.contains(id) && ("open".equals(status) || "closing_soon".equals(status))) {
                fresh.add(job);
            }
        }
        if (fresh.isEmpty()) {
            System.out.println("No new open jobs to alert.");
            return write(root, now, false, false, "no_new_jobs", 0, null, null);
        }
        String site = values.get("PUBLIC_SITE_URL");
        if (site == null || site.isBlank()) {
            site = "http://localhost:8090";
        }
        List<Map<String, Object>> shown = fresh.subList(0, Math.min(40, fresh.size()));
        String subject = "[Sarkari Jobs] " + fresh.size() + " new government/PSU job(s)";
        String from = first(values.get("SMTP_FROM"), values.get("SMTP_USER"), "jobs@" + host);
        Mail mail = new Mail(
                host,
                port(values.get("SMTP_PORT")),
                "true".equals(values.get("SMTP_SECURE")),
                blankToNull(values.get("SMTP_USER")),
                values.get("SMTP_PASS"),
                from,
                to,
                subject,
                textBody(shown, site),
                htmlBody(shown, site));
        try {
            mailer.send(mail);
        } catch (Exception ex) {
            String reason = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.toString() : ex.getMessage();
            System.err.println("Failed to send alert: " + reason);
            return write(root, now, false, true, reason, fresh.size(), null, null);
        }
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> job : fresh) {
            ids.add(job.get("id") == null ? "" : String.valueOf(job.get("id")));
        }
        System.out.println("Alert email sent for " + fresh.size() + " job(s) → " + to);
        return write(root, now, true, false, null, fresh.size(), ids, to);
    }

    private static Report write(
            Path root,
            String at,
            boolean sent,
            boolean failed,
            String reason,
            int newCount,
            List<String> ids,
            String to)
            throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("at", at);
        report.put("sent", sent);
        if (!sent) {
            report.put("reason", reason);
        }
        report.put("newCount", newCount);
        if (sent) {
            report.put("ids", ids);
            report.put("to", to);
        }
        BuildJobs.writeAtomic(root.resolve("data").resolve("processed").resolve("alert-report.json"), report);
        return new Report(sent, failed, reason, newCount);
    }

    private static List<Map<String, Object>> readJobs(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return List.of();
            }
            JsonNode node = MAPPER.readTree(file.toFile());
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> rows = MAPPER.convertValue(node, LIST);
            return rows == null ? List.of() : rows;
        } catch (IOException | RuntimeException ex) {
            return List.of();
        }
    }

    private static int port(String raw) {
        if (raw == null || raw.isBlank()) {
            return 587;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 587;
        }
    }

    private static String textBody(List<Map<String, Object>> jobs, String site) {
        StringBuilder text = new StringBuilder("New government/PSU jobs (").append(jobs.size()).append("):\n\n");
        for (Map<String, Object> job : jobs) {
            text.append("• ")
                    .append(text(job.get("title")))
                    .append("\n  ")
                    .append(text(job.get("organization")))
                    .append(" | ")
                    .append(text(job.get("selectionProcess")))
                    .append(" | Last: ")
                    .append(job.get("lastDate") == null || String.valueOf(job.get("lastDate")).isBlank()
                            ? "—"
                            : job.get("lastDate"))
                    .append("\n  ")
                    .append(text(job.get("officialUrl")))
                    .append("\n  Portal: ")
                    .append(site)
                    .append("/jobs/")
                    .append(text(job.get("id")))
                    .append("\n\n");
        }
        text.append("— Sarkari Jobs bot\n");
        return text.toString();
    }

    private static String htmlBody(List<Map<String, Object>> jobs, String site) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:system-ui,sans-serif;line-height:1.45\">");
        html.append("<h2>New government/PSU jobs (").append(jobs.size()).append(")</h2>");
        html.append("<p>Central / PSU / govt company openings (as classified).</p><ul>");
        for (Map<String, Object> job : jobs) {
            html.append("<li style=\"margin-bottom:12px\"><strong>")
                    .append(escape(text(job.get("title"))))
                    .append("</strong><br/>")
                    .append(escape(text(job.get("organization"))))
                    .append(" · ")
                    .append(escape(text(job.get("selectionProcess"))))
                    .append(" · Last date: ")
                    .append(escape(job.get("lastDate") == null || String.valueOf(job.get("lastDate")).isBlank()
                            ? "—"
                            : String.valueOf(job.get("lastDate"))))
                    .append("<br/><a href=\"")
                    .append(escape(text(job.get("officialUrl"))))
                    .append("\">Official notification</a>");
            if (job.get("id") != null && !String.valueOf(job.get("id")).isBlank()) {
                html.append(" · <a href=\"")
                        .append(escape(site))
                        .append("/jobs/")
                        .append(escape(String.valueOf(job.get("id"))))
                        .append("\">View on portal</a>");
            }
            html.append("</li>");
        }
        html.append("</ul><p style=\"color:#666;font-size:12px\">Always verify on the official site before applying. Not affiliated with GoI/PSUs.</p></div>");
        return html.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
