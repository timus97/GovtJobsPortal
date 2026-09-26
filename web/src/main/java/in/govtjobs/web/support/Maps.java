package in.govtjobs.web.support;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class Maps {

    private Maps() {}

    public static String str(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() || "null".equals(s) ? null : s;
    }

    public static boolean bool(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    public static List<?> list(Map<String, Object> map, String key) {
        if (map == null) return List.of();
        Object v = map.get(key);
        if (v instanceof List<?> l) return l;
        if (v instanceof Collection<?> c) return List.copyOf(c);
        return List.of();
    }
}
