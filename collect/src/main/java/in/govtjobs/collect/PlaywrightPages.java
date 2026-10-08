package in.govtjobs.collect;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Browser render for {@code render=browser} and NCS. An empty result is an error upstream;
 * this class does not fall back to the marketing homepage.
 */
public final class PlaywrightPages {

    private PlaywrightPages() {}

    public static List<SiteClient.PageBody> render(List<String> urls) throws IOException {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of("--disable-blink-features=AutomationControlled")));
            try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .setLocale("en-US")
                    .setViewportSize(1366, 768))) {
                context.addInitScript("Object.defineProperty(navigator, 'webdriver', { get: () => undefined });");
                Page page = context.newPage();
                List<SiteClient.PageBody> pages = new ArrayList<>();
                for (String url : urls) {
                    page.navigate(url, new Page.NavigateOptions().setTimeout(25_000).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    JsoupPages.refuseBotManager(page.url());
                    try {
                        page.waitForSelector("a[href]", new Page.WaitForSelectorOptions().setTimeout(8_000));
                    } catch (RuntimeException ignored) {
                        // The shell had no anchors in time. Return whatever rendered.
                    }
                    try {
                        page.waitForSelector(
                                "table tbody tr a, table tbody td, [role='row'] a",
                                new Page.WaitForSelectorOptions().setTimeout(6_000));
                    } catch (RuntimeException ignored) {
                        // A marketing shell has no vacancy table. Keep the rendered links.
                    }
                    String html = page.content();
                    String lower = html.toLowerCase(Locale.ROOT);
                    if (lower.contains("access denied") || lower.contains("you are being redirected") || lower.contains("just a moment")) {
                        page.waitForTimeout(4_000);
                        try {
                            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
                        } catch (RuntimeException ignored) {
                            // The interstitial did not settle. Keep the latest DOM.
                        }
                        JsoupPages.refuseBotManager(page.url());
                        html = page.content();
                    }
                    pages.add(new SiteClient.PageBody(page.url(), html));
                }
                return pages;
            } finally {
                browser.close();
            }
        } catch (RuntimeException ex) {
            throw new IOException(ex.getMessage() == null ? "playwright failed" : ex.getMessage(), ex);
        }
    }
}
