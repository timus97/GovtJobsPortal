package in.govtjobs.web.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonMaps {

    private JsonMaps() {}

    public static String str(Map<String, ?> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() || "null".equals(s) ? null : s;
    }

    public static String strOrEmpty(Map<String, ?> map, String key) {
        String s = str(map, key);
        return s == null ? "" : s;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            data.put(key, out);
            return out;
        }
        List<Map<String, Object>> empty = new ArrayList<>();
        data.put(key, empty);
        return empty;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), val));
            data.put(key, out);
            return out;
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        data.put(key, empty);
        return empty;
    }
}
