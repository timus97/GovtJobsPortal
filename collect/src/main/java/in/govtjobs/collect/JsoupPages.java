package in.govtjobs.collect;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

/** HTTPS fetch. Redirects stay on the same registrable site. Non-HTTPS list URLs are dropped. */
public final class JsoupPages {

    static final String USER_AGENT =
            "NoExamSarkariBot/1.0 (+https://github.com/local/govt-jobs-portal; research aggregator)";
    private static final long HOST_GAP_MS = 400;
    private static final Pattern IP_HOST = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
    private static final Pattern MULTI_SUFFIX = Pattern.compile("^(?:co|gov|nic|org|ac|net|res)\\.in$");
    private static final Pattern HOST_IN_TEXT = Pattern.compile("(?i)https?://([^/\\s:?#]+)");
    private static final ConcurrentHashMap<String, Object> HOST_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> HOST_LAST = new ConcurrentHashMap<>();

    private JsoupPages() {}

    public static SiteClient.PageBody fetch(String url) throws IOException {
        return fetch(url, true);
    }

    /** Bounded PDF download. A truncated body is refused so a cut-off file is not parsed. */
    public static byte[] fetchBytes(String url, int maxBytes) throws IOException {
        return fetchBytes(url, maxBytes, 0);
    }

    private static byte[] fetchBytes(String url, int maxBytes, int redirects) throws IOException {
        if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IOException("non-https link dropped: " + url);
        }
        if (redirects > 3) {
            throw new IOException("too many redirects");
        }
        polite(url);
        Connection.Response response = Jsoup.connect(url)
                .timeout(12_000)
                .followRedirects(false)
                .userAgent(USER_AGENT)
                .header("Accept", "application/pdf,*/*")
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .maxBodySize(maxBytes + 1)
                .execute();
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            String next = redirectTarget(url, response.header("Location"));
            if (!sameHost(url, next) || !next.regionMatches(true, 0, "https://", 0, 8)) {
                throw new IOException("redirect left the allowed host");
            }
            return fetchBytes(next, maxBytes, redirects + 1);
        }
        if (status >= 400) {
            throw new IOException("HTTP " + status);
        }
        String length = response.header("Content-Length");
        if (length != null && !length.isBlank()) {
            try {
                if (Long.parseLong(length.trim()) > maxBytes) {
                    throw new IOException("pdf larger than " + maxBytes);
                }
            } catch (NumberFormatException ignored) {
                // Read the body and enforce the cap below.
            }
        }
        byte[] body = response.bodyAsBytes();
        if (body.length > maxBytes) {
            throw new IOException("pdf larger than " + maxBytes);
        }
        return body;
    }

    static SiteClient.PageBody fetch(String url, boolean requireHttps) throws IOException {
        if (requireHttps && (url == null || !url.regionMatches(true, 0, "https://", 0, 8))) {
            throw new IOException("non-https link dropped: " + url);
        }
        return read(url, 0);
    }

    private static SiteClient.PageBody read(String url, int redirects) throws IOException {
        Connection.Response response = execute(url);
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            if (redirects >= 3) {
                throw new IOException("too many redirects");
            }
            String next = redirectTarget(url, response.header("Location"));
            if (!sameHost(url, next)) {
                throw new IOException("redirect left the allowed host");
            }
            return read(next, redirects + 1);
        }
        if (status >= 400) {
            throw new IOException("HTTP " + status);
        }
        return new SiteClient.PageBody(response.url().toString(), response.body());
    }

    static boolean sameHost(String left, String right) {
        String a = registrableSite(siteHost(SourceRules.host(left)));
        String b = registrableSite(siteHost(SourceRules.host(right)));
        return a != null && a.equals(b);
    }

    /**
     * Follows a redirect on the same site. A bot-manager interstitial is refused, and the error
     * names only that host so a query string is not copied into the report.
     */
    private static String redirectTarget(String url, String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IOException("javascript redirect");
        }
        if (botWall(location)) {
            throw botWallError(location);
        }
        String next;
        try {
            next = URI.create(url).resolve(location).toString();
        } catch (IllegalArgumentException ex) {
            if (botWall(location)) {
                throw botWallError(location);
            }
            throw new IOException("redirect left the allowed host");
        }
        if (botWall(next)) {
            throw botWallError(next);
        }
        return next;
    }

    /** A browser that lands on a bot-manager host is refused. The page URL is not returned. */
    static void refuseBotManager(String url) throws IOException {
        if (url != null && botWall(url)) {
            throw botWallError(url);
        }
    }

    private static boolean botWall(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("perfdrive") || lower.contains("radware") || lower.contains("botmanager");
    }

    private static IOException botWallError(String location) {
        String host = redirectHost(location);
        return new IOException(host == null ? "blocked: bot manager" : "blocked: bot manager " + host);
    }

    private static String redirectHost(String value) {
        String parsed = SourceRules.host(value);
        if (parsed != null && !parsed.isBlank()) {
            return parsed;
        }
        var matcher = HOST_IN_TEXT.matcher(value);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /** Last two labels, or three for co.in, gov.in, nic.in, org.in, ac.in, net.in, and res.in. */
    private static String registrableSite(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String bare = host.startsWith("www.") ? host.substring(4) : host;
        if (IP_HOST.matcher(bare).matches() || bare.indexOf(':') >= 0) {
            return bare;
        }
        String[] labels = bare.split("\\.");
        if (labels.length < 2) {
            return bare;
        }
        String tail = labels[labels.length - 2] + "." + labels[labels.length - 1];
        if (labels.length >= 3 && MULTI_SUFFIX.matcher(tail).matches()) {
            return labels[labels.length - 3] + "." + tail;
        }
        return tail;
    }

    private static Connection.Response execute(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            polite(url);
            try {
                Connection.Response response = Jsoup.connect(url)
                        .timeout(20_000)
                        .followRedirects(false)
                        .userAgent(USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .maxBodySize(2_000_000)
                        .execute();
                int status = response.statusCode();
                if (attempt == 0 && (status == 429 || status == 503)) {
                    pause(800);
                    continue;
                }
                return response;
            } catch (IOException ex) {
                last = ex;
                if (attempt == 0 && retryable(ex)) {
                    pause(400);
                    continue;
                }
                throw ex;
            }
        }
        throw last == null ? new IOException("fetch failed") : last;
    }

    private static void polite(String url) {
        String host = siteHost(SourceRules.host(url));
        if (host == null) {
            return;
        }
        Object lock = HOST_LOCKS.computeIfAbsent(host, key -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long wait = HOST_GAP_MS - (now - HOST_LAST.getOrDefault(host, 0L));
            if (wait > 0) {
                pause(wait);
            }
            HOST_LAST.put(host, System.currentTimeMillis());
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean retryable(IOException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(java.util.Locale.ROOT);
        return !message.contains("timed out") && !message.contains("timeout");
    }

    private static String siteHost(String host) {
        if (host == null) {
            return null;
        }
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
