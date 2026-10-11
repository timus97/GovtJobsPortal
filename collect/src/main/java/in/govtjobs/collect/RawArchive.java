package in.govtjobs.collect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Saves fetched HTML under data/raw and PDF bytes under data/raw/pdfs. A cache failure is ignored. */
final class RawArchive implements SiteClient {

    private final SiteClient delegate;
    private final Path root;
    private final String sourceId;
    private final String runId;

    RawArchive(SiteClient delegate, Path root, String sourceId, String runId) {
        this.delegate = delegate;
        this.root = root;
        this.sourceId = sourceId;
        this.runId = runId;
    }

    @Override
    public PageBody page(String url) throws IOException {
        PageBody body = delegate.page(url);
        save(body);
        return body;
    }

    @Override
    public List<PageBody> browser(List<String> urls) throws IOException {
        List<PageBody> pages = delegate.browser(urls);
        if (pages != null) {
            for (PageBody body : pages) {
                save(body);
            }
        }
        return pages;
    }

    @Override
    public byte[] bytes(String url) throws IOException {
        byte[] data = delegate.bytes(url);
        try {
            PdfDocuments.store(root, data);
        } catch (IOException ignored) {
            // The notice decision does not depend on the cache.
        }
        return data;
    }

    @Override
    public boolean documentPdf(String url) throws IOException {
        return delegate.documentPdf(url);
    }

    @Override
    public void rememberPdf(byte[] pdf, String text) {
        try {
            PdfDocuments.store(root, pdf);
            PdfDocuments.storeText(root, pdf, text);
        } catch (IOException ignored) {
            // A cache failure does not fail the collect.
        }
    }

    private void save(PageBody body) {
        if (body == null || body.html() == null) {
            return;
        }
        try {
            Path dir = root.resolve("data").resolve("raw").resolve(safe(sourceId)).resolve(safe(runId));
            Files.createDirectories(dir);
            String name = PdfDocuments.sha256(String.valueOf(body.url()).getBytes(StandardCharsets.UTF_8)).substring(0, 16)
                    + ".html";
            Path file = dir.resolve(name);
            if (!Files.exists(file)) {
                Files.writeString(file, body.html());
            }
        } catch (IOException ignored) {
            // The staging decision does not depend on the archive.
        }
    }

    private static String safe(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "source" : cleaned;
    }
}
