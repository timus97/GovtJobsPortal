package in.govtjobs.web.collect;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class HttpPageFetcher implements PageFetcher {

    private static final int MAX_BYTES = 256 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public PageFetch fetch(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "SarkariDeskCollect/1.0")
                .header("Accept", "text/html")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (body.length > MAX_BYTES) {
                byte[] cut = new byte[MAX_BYTES];
                System.arraycopy(body, 0, cut, 0, MAX_BYTES);
                body = cut;
            }
            return new PageFetch(response.statusCode(), new String(body, StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("fetch interrupted", ex);
        }
    }
}
