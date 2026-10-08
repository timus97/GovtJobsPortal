package in.govtjobs.collect;

import java.io.IOException;
import java.util.List;

/** Live HTTP plus Playwright. Fixture tests do not use this client. */
public final class LiveSiteClient implements SiteClient {

    private final Object browserLock = new Object();

    @Override
    public PageBody page(String url) throws IOException {
        return JsoupPages.fetch(url);
    }

    @Override
    public List<PageBody> browser(List<String> urls) throws IOException {
        synchronized (browserLock) {
            return PlaywrightPages.render(urls);
        }
    }

    @Override
    public byte[] bytes(String url) throws IOException {
        return JsoupPages.fetchBytes(url, 4_000_000);
    }
}
