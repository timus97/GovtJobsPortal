package in.govtjobs.web.collect;

import java.io.IOException;
import java.net.URI;

public interface PageFetcher {
    PageFetch fetch(URI uri) throws IOException;

    record PageFetch(int status, String body) {}
}
