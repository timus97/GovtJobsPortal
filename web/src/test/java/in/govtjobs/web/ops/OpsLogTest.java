package in.govtjobs.web.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpsLogTest {

    @Test
    void listFiltersLimitsAndDropsOldestPastFourHundred() {
        OpsLog log = new OpsLog();
        assertThat(log.list(null, 10)).isEmpty();
        assertThat(log.list("   ", 10)).isEmpty();

        log.info("collect", "Pasted SSC");
        log.info("publish", "Kept locally");
        List<Map<String, Object>> newest = log.list(null, 1);
        assertThat(newest).hasSize(1);
        assertThat(newest.get(0)).containsEntry("level", "info").containsEntry("unit", "publish");
        assertThat(String.valueOf(newest.get(0).get("at"))).isNotBlank();

        assertThat(log.list("ssc", 10)).hasSize(1);
        assertThat(log.list("NOPE", 10)).isEmpty();
        assertThat(log.list("INFO", 10)).hasSize(2);

        for (int i = 0; i < 401; i++) {
            log.info("unit", "m" + i);
        }
        List<Map<String, Object>> ring = log.list(null, 500);
        assertThat(ring).hasSize(400);
        assertThat(ring.get(0)).containsEntry("message", "m400");
        assertThat(ring.get(399)).containsEntry("message", "m1");
        assertThat(log.list("m0", 10)).isEmpty();
        assertThat(log.list("m400", 0)).hasSize(1);
    }
}
