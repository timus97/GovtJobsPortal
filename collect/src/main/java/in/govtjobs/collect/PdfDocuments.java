package in.govtjobs.collect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** PDF text is a vacancy only when {@link JobLinkQuality#isRecruitmentPdfText} says so. */
public final class PdfDocuments {

    private PdfDocuments() {}

    private static final Pattern TITLE_RE = Pattern.compile(
            "(?:engagement|recruitment|walk-?in interview|advertisement|notification)\\s+(?:of|for|:)\\s+([^.]{12,160})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VACANCY_RE = Pattern.compile(
            "(?:no\\.?\\s*of\\s*posts?|number of posts?|vacancies|vacancy)\\s*[:\\-]?\\s*(\\d{1,5})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTICE_LINE = Pattern.compile(
            "recruitment|vacancy|vacancies|walk[\\s-]?in|apprentice|notification|advertisement|advt\\.?\\s*no|engagement|consultant",
            Pattern.CASE_INSENSITIVE);

    public static String text(byte[] pdf) throws IOException {
        if (pdf == null || pdf.length == 0) {
            return "";
        }
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    public static Map<String, Object> parseFields(String text) {
        String raw = text == null ? "" : text;
        String compact = raw.replaceAll("\\s+", " ").trim();
        boolean notice = JobLinkQuality.isRecruitmentPdfText(raw);
        String title = null;
        Matcher titled = TITLE_RE.matcher(compact);
        if (titled.find()) {
            title = titled.group(1).trim();
        }
        if (title == null) {
            for (String line : raw.split("\\R")) {
                String trimmed = line.trim();
                if (NOTICE_LINE.matcher(trimmed).find() && trimmed.length() > 12 && trimmed.length() < 180) {
                    title = trimmed;
                    break;
                }
            }
        }
        Integer vacancies = null;
        Matcher vac = VACANCY_RE.matcher(compact);
        if (vac.find()) {
            vacancies = Integer.valueOf(vac.group(1));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("isJobNotice", notice);
        fields.put("title", title == null ? null : title.replaceAll("\\s+", " ").trim());
        fields.put("vacancies", vacancies);
        fields.put("lastDate", NoticeDates.findLastDateHint(raw));
        fields.put("excerpt", compact.substring(0, Math.min(compact.length(), 800)));
        return fields;
    }

    public static Path store(Path root, byte[] pdf) throws IOException {
        String hash = sha256(pdf);
        Path dir = root.resolve("data").resolve("raw").resolve("pdfs");
        Files.createDirectories(dir);
        Path file = dir.resolve(hash.toLowerCase(Locale.ROOT) + ".pdf");
        if (!Files.exists(file)) {
            Files.write(file, pdf);
        }
        return file;
    }

    public static String sha256(byte[] pdf) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf == null ? new byte[0] : pdf));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 missing", ex);
        }
    }
}
