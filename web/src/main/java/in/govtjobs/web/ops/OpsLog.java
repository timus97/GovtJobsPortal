package in.govtjobs.web.ops;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpsLog {

    private final Deque<Map<String, Object>> ring = new ArrayDeque<>();

    public synchronized void info(String unit, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("at", Instant.now().toString());
        row.put("level", "info");
        row.put("unit", unit);
        row.put("message", message);
        ring.addFirst(row);
        while (ring.size() > 400) ring.removeLast();
    }

    public synchronized List<Map<String, Object>> list(String q, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        String term = q == null ? "" : q.toLowerCase(Locale.ROOT);
        for (Map<String, Object> row : ring) {
            if (!term.isBlank() && !String.valueOf(row).toLowerCase(Locale.ROOT).contains(term)) continue;
            out.add(row);
            if (out.size() >= limit) break;
        }
        return out;
    }
}
