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
            String loose = null;
            for (String line : raw.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.length() <= 12 || trimmed.length() >= 180) {
                    continue;
                }
                if (JobLinkQuality.matchesNoticeText(trimmed)) {
                    title = trimmed;
                    break;
                }
                if (loose == null && trimmed.length() < 160) {
                    loose = trimmed;
                }
            }
            if (title == null) {
                title = loose;
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

    /** Plain text beside the cached PDF. A second write leaves the first extract in place. */
    public static void storeText(Path root, byte[] pdf, String text) throws IOException {
        if (text == null || text.isBlank()) {
            return;
        }
        String hash = sha256(pdf);
        Path dir = root.resolve("data").resolve("raw").resolve("pdfs");
        Files.createDirectories(dir);
        Path file = dir.resolve(hash.toLowerCase(Locale.ROOT) + ".txt");
        if (!Files.exists(file)) {
            Files.writeString(file, text);
        }
    }

    public static String sha256(byte[] pdf) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf == null ? new byte[0] : pdf));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 missing", ex);
        }
    }
}
