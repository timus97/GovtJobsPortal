package in.govtjobs.web.collect;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Structured log for collector jobs. Lines go to the collect logger and to collect.job_log. */
@Component
public class CrawlJobLogger {

    static final Logger LOG = LoggerFactory.getLogger("in.govtjobs.collect");

    private final JdbcTemplate jdbc;

    public CrawlJobLogger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void info(String runId, String source, String phase, String message) {
        write("INFO", runId, source, phase, message);
    }

    public void error(String runId, String source, String phase, String message) {
        write("ERROR", runId, source, phase, message);
    }

    public List<Map<String, Object>> forRun(String runId) {
        return jdbc.queryForList(
                """
                SELECT at, level, source_name AS "sourceName", phase, message
                FROM collect.job_log
                WHERE run_id = ?
                ORDER BY id
                """,
                runId);
    }

    private void write(String level, String runId, String source, String phase, String message) {
        String safe = message == null ? "" : message.replaceAll("[\\r\\n]", " ").trim();
        if (safe.length() > 500) {
            safe = safe.substring(0, 500);
        }
        String src = source == null ? "" : source;
        String ph = phase == null ? "" : phase;
        if ("ERROR".equals(level)) {
            LOG.error("runId={} source={} phase={} {}", runId, src, ph, safe);
        } else {
            LOG.info("runId={} source={} phase={} {}", runId, src, ph, safe);
        }
        jdbc.update(
                """
                INSERT INTO collect.job_log (level, run_id, source_name, phase, message)
                VALUES (?, ?, ?, ?, ?)
                """,
                level,
                runId,
                src,
                ph,
                safe);
    }
}
