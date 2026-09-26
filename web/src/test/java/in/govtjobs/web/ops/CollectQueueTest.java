package in.govtjobs.web.ops;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.govtjobs.web.config.GovtJobsProperties;
import in.govtjobs.web.store.JobStore;
import org.junit.jupiter.api.Test;

class CollectQueueTest {

    @Test
    void enqueueRejectsConfusedHostAndHttp() {
        GovtJobsProperties props = new GovtJobsProperties();
        CollectQueue queue = new CollectQueue(
                new ObjectMapper(), new OfficialUrlPolicy(props), props, new JobStore(new ObjectMapper()));
        assertThatThrownBy(() -> queue.enqueue("https://gov.in.evil.com/", "evil"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.enqueue("http://ssc.gov.in/notice", "http"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }
}
