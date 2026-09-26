package in.govtjobs.web.store;

import in.govtjobs.domain.job.JobSchema;
import in.govtjobs.web.config.GovtJobsProperties;
import jakarta.annotation.PostConstruct;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogStore {

    private final JdbcTemplate jdbc;
    private final GovtJobsProperties props;

    public CatalogStore(JdbcTemplate jdbc, GovtJobsProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @PostConstruct
    public void seedIfEmpty() {
        if (!props.getCatalog().isSeedDummy()) {
            return;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.opportunities", Integer.class);
        if (n != null && n > 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        insertJob("sample-ssc-cgl", "Combined Graduate Level — sample window", "Staff Selection Commission",
                "central", "Administration", "All India", "graduate", "written_multi_stage", true,
                today.plusDays(18), "https://ssc.gov.in/", "Open sample. Check the official SSC notice before you act.",
                "approved");
        insertJob("sample-ibps-po", "Probationary Officer — sample window", "Institute of Banking Personnel Selection",
                "central", "Banking", "All India", "graduate", "written_multi_stage", true,
                today.plusDays(4), "https://www.ibps.in/", "Closing soon in this sample. The date is illustrative.",
                "approved");
        insertJob("sample-rrb-ntpc", "NTPC — sample window", "Railway Recruitment Boards",
                "central", "Railways", "All India", "12th", "cbt", true,
                today.plusDays(40), "https://www.rrbapply.gov.in/", "Sample only. Open the official RRB notice before you act.",
                "approved");
        insertJob("sample-bel-walkin", "Walk-in interview — sample", "Bharat Electronics Limited",
                "psu", "Defence", "Bengaluru", "diploma", "walk_in", false,
                today.plusDays(12), "https://bel-india.in/", "No written exam in this sample card. Confirm the walk-in notice.",
                "approved");
        insertJob("sample-esic-closed", "Nursing Officer — sample, already closed", "Employees' State Insurance Corporation",
                "central", "Health", "Delhi", "graduate", "written_multi_stage", true,
                today.minusDays(12), "https://www.esic.gov.in/", "Closed sample so the desk can show a finished window.",
                "approved");
        insertJob("sample-hidden-review", "Needs admin approval — must stay hidden", "Sample Board",
                "central", "Other", "Delhi", "graduate", "interview_only", false,
                today.plusDays(30), "https://www.india.gov.in/", "This row is waiting for an admin. Students must not see it.",
                "needs_review");

        insertSeries("sample-ssc-cgl-series", "SSC CGL", "SSC", "Sample cycle", false,
                "https://ssc.gov.in/", today.plusDays(80), "sample-ssc-cgl",
                "A calendar row. Apply only appears when a linked sample window is still open.");
        insertSeries("sample-gate", "GATE", "IIT", "Sample cycle", true,
                "https://gate2027.iitm.ac.in/", today.plusDays(100), "",
                "Prepare-for sample. This is a score exam, not a vacancy.");
        insertSeries("sample-upsc-cse", "UPSC CSE", "UPSC", "Sample cycle", false,
                "https://upsc.gov.in/", today.plusDays(140), "",
                "No linked open window in the sample, so there is no Apply button.");
    }

    public List<Map<String, Object>> approvedJobs() {
        return jdbc.query(
                """
                SELECT id, title, organization, org_type, sector, location, qualification,
                       selection_process, has_exam, last_date, official_url, source_name, summary, sample
                FROM catalog.opportunities
                WHERE review_status = 'approved'
                ORDER BY last_date NULLS LAST
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Date last = rs.getDate("last_date");
                    String lastDate = last == null ? "" : last.toLocalDate().toString();
                    row.put("id", rs.getString("id"));
                    row.put("title", rs.getString("title"));
                    row.put("organization", rs.getString("organization"));
                    row.put("orgType", rs.getString("org_type"));
                    row.put("sector", rs.getString("sector"));
                    row.put("location", rs.getString("location"));
                    row.put("qualification", rs.getString("qualification"));
                    row.put("selectionProcess", rs.getString("selection_process"));
                    row.put("hasExam", rs.getBoolean("has_exam"));
                    row.put("lastDate", lastDate);
                    row.put("officialUrl", rs.getString("official_url"));
                    row.put("sourceName", rs.getString("source_name"));
                    row.put("sourceId", "sample");
                    row.put("sourceUrl", rs.getString("official_url"));
                    row.put("summary", rs.getString("summary"));
                    row.put("sample", rs.getBoolean("sample"));
                    row.put("vacancies", "");
                    row.put("notificationDate", "");
                    row.put("status", JobSchema.computeStatus(lastDate));
                    return row;
                });
    }

    public List<Map<String, Object>> approvedSeries() {
        List<Map<String, Object>> rows = jdbc.query(
                """
                SELECT id, name, board, cycle, apply_never, official_url, expected_exam, linked_ids, summary, sample
                FROM catalog.exam_series
                WHERE review_status = 'approved'
                ORDER BY name
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Date exam = rs.getDate("expected_exam");
                    row.put("id", rs.getString("id"));
                    row.put("name", rs.getString("name"));
                    row.put("board", rs.getString("board"));
                    row.put("cycle", rs.getString("cycle"));
                    row.put("applyNever", rs.getBoolean("apply_never"));
                    row.put("officialUrl", rs.getString("official_url"));
                    row.put("expectedExam", exam == null ? "" : exam.toLocalDate().toString());
                    row.put("expectedNotify", "");
                    row.put("expectedApply", "");
                    row.put("minEducation", "");
                    row.put("summary", rs.getString("summary"));
                    row.put("sample", rs.getBoolean("sample"));
                    List<String> ids = new ArrayList<>();
                    String linked = rs.getString("linked_ids");
                    if (linked != null && !linked.isBlank()) {
                        for (String part : linked.split(",")) {
                            if (!part.isBlank()) ids.add(part.trim());
                        }
                    }
                    row.put("linkedOpportunityIds", ids);
                    return row;
                });
        return rows;
    }

    public boolean sampleMode() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog.opportunities WHERE sample = TRUE AND review_status = 'approved'",
                Integer.class);
        return n != null && n > 0;
    }

    private void insertJob(
            String id, String title, String org, String orgType, String sector, String location, String qual,
            String selection, boolean hasExam, LocalDate last, String url, String summary, String review) {
        jdbc.update(
                """
                INSERT INTO catalog.opportunities
                  (id, title, organization, org_type, sector, location, qualification, selection_process,
                   has_exam, last_date, official_url, source_name, review_status, summary, sample)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'Sample catalog',?,?,TRUE)
                """,
                id, title, org, orgType, sector, location, qual, selection, hasExam,
                Date.valueOf(last), url, review, summary);
    }

    private void insertSeries(
            String id, String name, String board, String cycle, boolean applyNever, String url,
            LocalDate expected, String linked, String summary) {
        jdbc.update(
                """
                INSERT INTO catalog.exam_series
                  (id, name, board, cycle, apply_never, official_url, expected_exam, review_status, linked_ids, summary, sample)
                VALUES (?,?,?,?,?,?,?,'approved',?,?,TRUE)
                """,
                id, name, board, cycle, applyNever, url, Date.valueOf(expected), linked, summary);
    }
}
