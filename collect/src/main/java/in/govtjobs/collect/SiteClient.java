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

    /** Notice PDF bytes. Tests leave this unimplemented so a fixture never downloads. */
    default byte[] bytes(String url) throws IOException {
        throw new IOException("pdf download not available");
    }

    record PageBody(String url, String html) {}
}
