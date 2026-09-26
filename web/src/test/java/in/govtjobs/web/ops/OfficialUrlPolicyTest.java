package in.govtjobs.web.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.govtjobs.web.config.GovtJobsProperties;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class OfficialUrlPolicyTest {

    private final OfficialUrlPolicy policy = new OfficialUrlPolicy(new GovtJobsProperties());

    @Test
    void rejectsSuffixConfusionAndHttp() {
        assertThatThrownBy(() -> policy.requireAllowed("https://gov.in.evil.com/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed("http://ssc.gov.in/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(() -> policy.requireAllowed("https://127.0.0.1/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://user:pass@ssc.gov.in/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://[::1]/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(policy.isAllowedHost("ssc.gov.in")).isTrue();
        assertThat(policy.isAllowedHost("ibps.in")).isTrue();
        assertThat(policy.isAllowedHost("www.ibps.in")).isTrue();
        assertThat(policy.isAllowedHost("gov.in.evil.com")).isFalse();
        assertThat(policy.isAllowedHost("localhost")).isFalse();
    }

    @Test
    void blocksCarrierGradeNatAndUniqueLocal() throws Exception {
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("100.64.0.1"))).isTrue();
        byte[] ula = new byte[16];
        ula[0] = (byte) 0xfd;
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByAddress(ula))).isTrue();
        byte[] mappedLoopback = new byte[16];
        mappedLoopback[10] = (byte) 0xff;
        mappedLoopback[11] = (byte) 0xff;
        mappedLoopback[12] = 127;
        mappedLoopback[15] = 1;
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByAddress(mappedLoopback))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
    }
}
