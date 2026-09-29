package in.govtjobs.web.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.govtjobs.web.config.GovtJobsProperties;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfficialUrlPolicyMoreTest {

    @Test
    void requireHttpsRejectsBlankUserInfoAndBrokenUris() {
        OfficialUrlPolicy policy = new OfficialUrlPolicy(new GovtJobsProperties());
        assertThat(OfficialUrlPolicy.isHttps(null)).isFalse();
        assertThat(OfficialUrlPolicy.isHttps("  HTTPS://ssc.gov.in/a")).isTrue();
        assertThat(OfficialUrlPolicy.isHttps("http://ssc.gov.in")).isFalse();

        assertThat(policy.requireHttps("  https://ssc.gov.in/notice ").getHost()).isEqualTo("ssc.gov.in");
        assertThatThrownBy(() -> policy.requireHttps(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireHttps("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireHttps("https://user:pass@ssc.gov.in/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireHttps("https://")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireHttps("https://[")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://example.com/jobs"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("official site");
        assertThatThrownBy(() -> policy.requireAllowed("https://metadata.google.internal/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://instance-data/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suffixesExtrasAndLiteralAddresses() throws Exception {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getOps().setAllowedSuffixes(java.util.Arrays.asList("nic.in", " ", null, ".gov.in"));
        props.getOps().setExtraHosts(java.util.Arrays.asList("www.example.org", " ", null, "jobs.example.org"));
        OfficialUrlPolicy policy = new OfficialUrlPolicy(props);

        assertThat(policy.isAllowedHost(null)).isFalse();
        assertThat(policy.isAllowedHost("  ")).isFalse();
        assertThat(policy.isAllowedHost("1.2.3.4")).isFalse();
        assertThat(policy.isAllowedHost("metadata")).isFalse();
        assertThat(policy.isAllowedHost("metadata.goog")).isFalse();
        assertThat(policy.isAllowedHost("ssc.nic.in")).isTrue();
        assertThat(policy.isAllowedHost("nic.in")).isTrue();
        assertThat(policy.isAllowedHost("gov.in")).isTrue();
        assertThat(policy.isAllowedHost("ssc.gov.in")).isTrue();
        assertThat(policy.isAllowedHost("example.org")).isTrue();
        assertThat(policy.isAllowedHost("www.example.org")).isTrue();
        assertThat(policy.isAllowedHost("careers.example.org")).isTrue();
        assertThat(policy.isAllowedHost("notexample.org")).isFalse();
        assertThat(policy.isAllowedHost("jobs.example.org")).isTrue();

        props.getOps().setAllowedSuffixes(null);
        props.getOps().setExtraHosts(null);
        assertThat(policy.isAllowedHost("ssc.gov.in")).isFalse();

        assertThat(OfficialUrlPolicy.isLiteralIp(null)).isFalse();
        assertThat(OfficialUrlPolicy.isLiteralIp(" ")).isFalse();
        assertThat(OfficialUrlPolicy.isLiteralIp("::1")).isTrue();
        assertThat(OfficialUrlPolicy.isLiteralIp("1.2.3.4")).isTrue();
        assertThat(OfficialUrlPolicy.isLiteralIp("1.2.3")).isFalse();
        assertThat(OfficialUrlPolicy.isLiteralIp("ssc.gov.in")).isFalse();

        assertThat(OfficialUrlPolicy.isBlockedAddress(null)).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("0.0.0.0"))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("10.1.1.1"))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("172.16.0.1"))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("192.168.1.1"))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("169.254.1.1"))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("224.0.0.1"))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("100.64.0.0"))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("100.127.255.255"))).isTrue();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("100.63.255.255"))).isFalse();
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByName("100.128.0.1"))).isFalse();

        byte[] fc = new byte[16];
        fc[0] = (byte) 0xfc;
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByAddress(fc))).isTrue();
        byte[] publicV6 = new byte[16];
        publicV6[0] = 0x20;
        publicV6[1] = 0x01;
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByAddress(publicV6))).isFalse();

        byte[] mappedPublic = new byte[16];
        mappedPublic[10] = (byte) 0xff;
        mappedPublic[11] = (byte) 0xff;
        mappedPublic[12] = 8;
        mappedPublic[13] = 8;
        mappedPublic[14] = 8;
        mappedPublic[15] = 8;
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByAddress(mappedPublic))).isFalse();

        byte[] notMapped = new byte[16];
        notMapped[10] = (byte) 0xff;
        assertThat(OfficialUrlPolicy.isBlockedAddress(InetAddress.getByAddress(notMapped))).isFalse();

        Method resolves = OfficialUrlPolicy.class.getDeclaredMethod("resolvesToBlockedAddress", String.class);
        resolves.setAccessible(true);
        assertThat((boolean) resolves.invoke(null, "127.0.0.1")).isTrue();
        assertThat((boolean) resolves.invoke(null, "8.8.8.8")).isFalse();
        assertThat((boolean) resolves.invoke(null, "no-such-host.invalid")).isTrue();
    }

    @Test
    void emptyExtraListDoesNotMatch() {
        GovtJobsProperties props = new GovtJobsProperties();
        props.getOps().setAllowedSuffixes(List.of());
        props.getOps().setExtraHosts(List.of(""));
        OfficialUrlPolicy policy = new OfficialUrlPolicy(props);
        assertThat(policy.isAllowedHost("ibps.in")).isFalse();
    }
}
