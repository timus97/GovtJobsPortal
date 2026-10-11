package in.govtjobs.collect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Pages for one collect run. Tests supply fixtures. Live runs use HTTP and Playwright. */
public interface SiteClient {

    PageBody page(String url) throws IOException;

    default List<PageBody> browser(List<String> urls) throws IOException {
        List<PageBody> pages = new ArrayList<>();
        for (String url : urls) {
            pages.add(page(url));
        }
        return pages;
    }

    /**
     * Notice or calendar PDF bytes. The default fails closed: a fixture that does not
     * supply bytes does not publish the anchor without evidence.
     */
    default byte[] bytes(String url) throws IOException {
        throw new IOException("pdf download not available");
    }

    /**
     * A {@code .pdf} href is a PDF. Other document hrefs are a PDF only when a live client
     * reads a PDF content type. Fixtures stay on the extension.
     */
    default boolean documentPdf(String url) throws IOException {
        return url != null && url.matches("(?i).*\\.pdf(?:\\?|#|$).*");
    }

    /** Cache a downloaded PDF. The default keeps nothing; the daily run stores bytes and text. */
    default void rememberPdf(byte[] pdf, String text) {}

    record PageBody(String url, String html) {}
}
