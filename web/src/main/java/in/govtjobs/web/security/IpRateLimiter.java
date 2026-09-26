package in.govtjobs.web.security;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory IP buckets. Node uses a Map; same idea, no extra dependency. */
final class IpRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    boolean allow(String key, int max, long windowMs, long now) {
        if (key == null || key.isBlank() || max <= 0 || windowMs <= 0) {
            return true;
        }
        prune(now);
        Bucket next = buckets.compute(key, (k, cur) -> {
            if (cur == null || cur.resetAt <= now) {
                return new Bucket(1, now + windowMs);
            }
            cur.count += 1;
            return cur;
        });
        return next.count <= max;
    }

    long retryAfterSeconds(String key, long now) {
        Bucket cur = buckets.get(key);
        if (cur == null || cur.resetAt <= now) {
            return 1L;
        }
        return Math.max(1L, (cur.resetAt - now + 999L) / 1000L);
    }

    private void prune(long now) {
        if (buckets.size() < 500) {
            return;
        }
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Bucket b = it.next().getValue();
            if (b == null || b.resetAt <= now) {
                it.remove();
            }
        }
    }

    private static final class Bucket {
        private int count;
        private final long resetAt;

        private Bucket(int count, long resetAt) {
            this.count = count;
            this.resetAt = resetAt;
        }
    }
}
