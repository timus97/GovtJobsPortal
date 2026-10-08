package in.govtjobs.collect;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** One registry row. Manual and disabled rows are not fetched by a daily run. */
public record SourceSpec(
        String sourceId,
        String name,
        String collector,
        String method,
        String render,
        String orgType,
        List<String> listUrls,
        boolean enabled) {

    public static SourceSpec from(Map<String, ?> row) {
        String sourceId = text(row.get("sourceId"));
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (row.get("listUrls") instanceof List<?> list) {
            for (Object url : list) {
                if (url != null && !String.valueOf(url).isBlank()) {
                    urls.add(String.valueOf(url));
                }
            }
        }
        String base = text(row.get("baseUrl"));
        if (!base.isBlank()) {
            urls.add(base);
        }
        String org = text(row.get("orgTypeDefault"));
        if (org.isBlank()) {
            org = text(row.get("orgType"));
        }
        if (org.isBlank()) {
            org = "central";
        }
        String collector = text(row.get("collector"));
        return new SourceSpec(
                sourceId,
                text(row.get("name")).isBlank() ? sourceId : text(row.get("name")),
                collector,
                text(row.get("method")),
                text(row.get("render")),
                org,
                List.copyOf(urls),
                Boolean.TRUE.equals(row.get("enabled")));
    }

    public boolean browser() {
        return "browser".equals(render) || "browser_scrape".equals(method);
    }

    public boolean manual() {
        return "manual".equals(method);
    }

    public boolean ncs() {
        return "ncs".equals(collector) || sourceId.startsWith("ncs");
    }

    public boolean rrb() {
        return "rrb".equals(collector) || sourceId.startsWith("rrb");
    }

    public boolean ibps() {
        return "ibps".equals(collector) || sourceId.contains("ibps");
    }

    public boolean upsc() {
        return "upsc".equals(collector) || sourceId.startsWith("upsc");
    }

    public boolean ssc() {
        return "ssc".equals(collector) || sourceId.startsWith("ssc");
    }

    public boolean calendar() {
        return sourceId.endsWith("_calendar") || "calendar".equals(collector);
    }

    public boolean defence() {
        return sourceId.startsWith("defence");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static List<SourceSpec> enabled(List<Map<String, Object>> rows) {
        List<SourceSpec> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            SourceSpec spec = from(row);
            if (spec.enabled() && !spec.manual() && !spec.sourceId().isBlank()) {
                out.add(spec);
            }
        }
        return out;
    }
}
