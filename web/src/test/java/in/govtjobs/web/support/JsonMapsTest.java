package in.govtjobs.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonMapsTest {

    @Test
    void strCoversNullBlankAndNullWord() throws Exception {
        assertThat(JsonMaps.str(null, "a")).isNull();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("missing", null);
        map.put("blank", "  ");
        map.put("word", "null");
        map.put("num", 7);
        map.put("ok", " Delhi ");
        assertThat(JsonMaps.str(map, "missing")).isNull();
        assertThat(JsonMaps.str(map, "blank")).isNull();
        assertThat(JsonMaps.str(map, "word")).isNull();
        assertThat(JsonMaps.str(map, "absent")).isNull();
        assertThat(JsonMaps.str(map, "num")).isEqualTo("7");
        assertThat(JsonMaps.str(map, "ok")).isEqualTo(" Delhi ");
        assertThat(JsonMaps.strOrEmpty(null, "a")).isEmpty();
        assertThat(JsonMaps.strOrEmpty(map, "ok")).isEqualTo(" Delhi ");
        Constructor<JsonMaps> ctor = JsonMaps.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }

    @Test
    void listOfKeepsMapsAndReplacesOtherShapes() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> row = Map.of("id", "1");
        data.put("jobs", new ArrayList<>(List.of(row, "skip", 3)));
        List<Map<String, Object>> listed = JsonMaps.listOf(data, "jobs");
        assertThat(listed).containsExactly(row);
        assertThat(data.get("jobs")).isSameAs(listed);

        data.put("jobs", "nope");
        List<Map<String, Object>> empty = JsonMaps.listOf(data, "jobs");
        assertThat(empty).isEmpty();
        assertThat(data.get("jobs")).isSameAs(empty);

        data.remove("jobs");
        assertThat(JsonMaps.listOf(data, "jobs")).isEmpty();
    }

    @Test
    void mapOfCopiesEntriesAndDefaultsToEmpty() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<Object, Object> raw = new LinkedHashMap<>();
        raw.put("title", "Clerk");
        raw.put(2, null);
        data.put("extracted", raw);
        Map<String, Object> copied = JsonMaps.mapOf(data, "extracted");
        assertThat(copied).containsEntry("title", "Clerk").containsEntry("2", null);
        assertThat(data.get("extracted")).isSameAs(copied);

        data.put("extracted", List.of());
        Map<String, Object> empty = JsonMaps.mapOf(data, "extracted");
        assertThat(empty).isEmpty();
        assertThat(data.get("extracted")).isSameAs(empty);

        data.put("extracted", Set.of());
        assertThat(JsonMaps.mapOf(data, "extracted")).isEmpty();
    }
}
