package in.govtjobs.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapsTest {

    @Test
    void strBoolAndListCoverEachBranch() throws Exception {
        assertThat(Maps.str(null, "a")).isNull();
        assertThat(Maps.bool(null, "a")).isFalse();
        assertThat(Maps.list(null, "a")).isEmpty();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("blank", " ");
        map.put("word", "null");
        map.put("text", "yes");
        map.put("flag", Boolean.TRUE);
        map.put("no", Boolean.FALSE);
        map.put("truthy", "TRUE");
        map.put("other", 1);
        map.put("rows", List.of("a"));
        map.put("set", new LinkedHashSet<>(Set.of("b")));
        map.put("scalar", "c");

        assertThat(Maps.str(map, "blank")).isNull();
        assertThat(Maps.str(map, "word")).isNull();
        assertThat(Maps.str(map, "missing")).isNull();
        assertThat(Maps.str(map, "text")).isEqualTo("yes");
        assertThat(Maps.bool(map, "flag")).isTrue();
        assertThat(Maps.bool(map, "no")).isFalse();
        assertThat(Maps.bool(map, "truthy")).isTrue();
        assertThat(Maps.bool(map, "other")).isFalse();
        assertThat(Maps.bool(map, "missing")).isFalse();
        assertThat(Maps.list(map, "rows")).hasSize(1);
        assertThat(Maps.list(map, "set")).hasSize(1);
        assertThat(Maps.list(map, "scalar")).isEmpty();
        assertThat(Maps.list(map, "missing")).isEmpty();

        Constructor<Maps> ctor = Maps.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
