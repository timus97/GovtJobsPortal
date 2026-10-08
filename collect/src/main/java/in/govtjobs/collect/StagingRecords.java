package in.govtjobs.collect;

import in.govtjobs.domain.job.JobSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One staging row per kept notice. {@code eligibilityParse.complete} stays false. */
public final class StagingRecords {

    private StagingRecords() {}

    private static final Pattern PDF_TAIL = Pattern.compile(
            "\\s*\\(\\s*\\|?\\s*PDF\\s*\\|[^)]*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF_SIZE = Pattern.compile(
            "[-–—]?\\s*PDF size:\\s*\\([^)]*\\)\\s*\\.?|\\s*PDF\\s*\\(\\s*Size:[^)]*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LANG_TAIL = Pattern.compile("\\s*\\|\\s*(PDF|English|Hindi)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTICE_NO = Pattern.compile(
            "(?:advt(?:ertisement)?|notification|vacancy)\\s*(?:no\\.?|number|#)?\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9/\\-.]{3,40})",
            Pattern.CASE_INSENSITIVE);

    public static String cleanTitle(String raw) {
        String title = raw == null ? "" : raw.replaceAll("\\s+", " ");
        title = PDF_TAIL.matcher(title).replaceAll("");
        title = PDF_SIZE.matcher(title).replaceAll("");
        title = LANG_TAIL.matcher(title).replaceAll("");
        return title.trim();
    }

    public static Map<String, Object> toStagingRecord(Map<String, ?> item, SourceSpec source, String collectedAt, String collectorVersion) {
        String title = cleanTitle(text(item.get("title")));
        String officialUrl = first(item.get("href"), item.get("officialUrl"));
        String summary = text(first(item.get("summary"), item.get("text"), title));
        if (summary.length() > 800) {
            summary = summary.substring(0, 800);
        }
        String blob = title + " " + summary + " " + text(item.get("extraText")) + " " + text(item.get("pdfText"));
        JobSchema.ClassificationResult classified = JobSchema.classifySelectionText(blob);
        String selectionProcess = textOrNull(item.get("selectionProcess"));
        Boolean hasExam = item.get("hasExam") instanceof Boolean value ? value : null;
        boolean needsReview = false;
        if (Boolean.TRUE.equals(classified.hasExam())) {
            hasExam = true;
            if (classified.selectionProcess() != null) {
                selectionProcess = classified.selectionProcess();
            }
            if (selectionProcess == null) {
                needsReview = true;
                selectionProcess = "written_multi_stage";
            }
        } else if (Boolean.TRUE.equals(hasExam)) {
            if (classified.selectionProcess() != null) {
                selectionProcess = classified.selectionProcess();
            }
            if (selectionProcess == null) {
                needsReview = true;
                selectionProcess = "written_multi_stage";
            }
        } else if (classified.selectionProcess() != null) {
            selectionProcess = classified.selectionProcess();
            hasExam = false;
        } else if (blob.matches("(?i).*walk[\\s-]?in.*")) {
            selectionProcess = "walk_in";
            hasExam = false;
        } else if (blob.toLowerCase(Locale.ROOT).contains("apprentice")) {
            selectionProcess = "apprenticeship";
            hasExam = false;
        } else if (blob.matches("(?i).*consultant.*|.*contract.*")) {
            selectionProcess = "contract_interview";
            hasExam = false;
        } else if (blob.matches("(?i).*interview.*|.*shortlist.*")) {
            selectionProcess = "interview_only";
            hasExam = false;
        } else if (blob.matches("(?i).*direct\\s+recruitment.*")) {
            selectionProcess = "direct_recruitment";
            hasExam = false;
        } else {
            needsReview = true;
            if (selectionProcess == null) {
                selectionProcess = "interview_only";
            }
            hasExam = false;
        }
        String lastDate = textOrNull(item.get("lastDate"));
        if (lastDate == null) {
            lastDate = NoticeDates.findLastDateHint(blob);
        }
        if (lastDate == null) {
            lastDate = NoticeDates.findLastDateHint(title);
        }
        if (lastDate == null) {
            lastDate = NoticeDates.lastDateIn(text(item.get("text")));
        }
        String organization = textOrNull(item.get("organization"));
        if (organization == null) {
            organization = source.name().replaceAll("(?i)\\s+Careers$", "").trim();
        }
        if (organization.isBlank()) {
            organization = source.sourceId();
        }
        Matcher notice = NOTICE_NO.matcher(blob);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("title", title.isBlank() ? "Untitled vacancy" : title);
        record.put("organization", organization);
        record.put("orgType", textOrNull(item.get("orgType")) == null ? source.orgType() : text(item.get("orgType")));
        record.put("sector", textOrNull(item.get("sector")) == null ? "Other" : text(item.get("sector")));
        record.put("location", textOrNull(item.get("location")) == null ? "All India" : text(item.get("location")));
        record.put("vacancies", item.get("vacancies"));
        record.put("qualification", item.get("qualification"));
        record.put("experience", item.get("experience"));
        record.put("salary", item.get("salary"));
        record.put("selectionProcess", selectionProcess);
        record.put("hasExam", Boolean.TRUE.equals(hasExam));
        record.put("applicationMode", blob.matches("(?i).*walk[\\s-]?in.*") ? "walk_in" : "online");
        record.put("notificationDate", item.get("notificationDate"));
        record.put("lastDate", lastDate);
        record.put("walkInDate", item.get("walkInDate"));
        record.put("officialUrl", officialUrl);
        record.put("sourceId", source.sourceId());
        record.put("sourceName", source.name());
        record.put("sourceUrl", textOrNull(item.get("sourceUrl")) == null ? officialUrl : text(item.get("sourceUrl")));
        record.put("summary", summary.isBlank() ? "Scraped vacancy from " + source.name() + ". Verify selection process on the official page." : summary);
        record.put("eligibility", item.get("eligibility") instanceof List<?> list ? list : List.of());
        record.put("processSteps", steps(item, Boolean.TRUE.equals(hasExam)));
        record.put("documentsRequired", item.get("documentsRequired") instanceof List<?> docs ? docs : List.of());
        record.put("needsReview", needsReview || lastDate == null);
        record.put(
                "selectionInferred",
                classified.selectionProcess() != null && classified.reason() != null && !"unknown".equals(classified.reason()));
        record.put("notificationNo", textOrNull(item.get("notificationNo")) == null ? (notice.find() ? notice.group(1) : null) : item.get("notificationNo"));
        record.put("collectedAt", collectedAt);
        record.put("collectorVersion", collectorVersion == null ? "scrape-v1" : collectorVersion);
        record.put("eligibilityParse", Map.of("complete", false));
        if (item.get("examDate") != null) {
            record.put("examDate", item.get("examDate"));
        }
        return record;
    }

    public static List<Map<String, Object>> dedupeByUrl(List<Map<String, Object>> records) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            String url = text(record.get("officialUrl")).toLowerCase(Locale.ROOT).split("\\?", 2)[0];
            if (url.isEmpty()) {
                continue;
            }
            String key = url
                    + "|"
                    + text(record.get("title")).toLowerCase(Locale.ROOT)
                    + "|"
                    + text(record.get("organization")).toLowerCase(Locale.ROOT);
            map.putIfAbsent(key, record);
        }
        return new ArrayList<>(map.values());
    }

    private static List<String> steps(Map<String, ?> item, boolean exam) {
        if (item.get("processSteps") instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object step : list) {
                if (step != null) {
                    out.add(String.valueOf(step));
                }
            }
            return out;
        }
        if (exam) {
            return List.of(
                    "Open the official notification link",
                    "Confirm eligibility on the official advertisement",
                    "Apply only through the official channel before the last date");
        }
        return List.of(
                "Open the official notification link",
                "Confirm eligibility and that selection has no written exam/CBT",
                "Apply only through the official channel before the last date");
    }

    private static String first(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String textOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value);
    }
}
