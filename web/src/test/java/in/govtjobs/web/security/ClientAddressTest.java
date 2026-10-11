package in.govtjobs.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientAddressTest {

    @Test
    void loopbackIsOnlyANumericLiteral() {
        assertThat(ClientAddress.isLoopback("127.0.0.1")).isTrue();
        assertThat(ClientAddress.isLoopback("  127.0.0.2  ")).isTrue();
        assertThat(ClientAddress.isLoopback("127.255.255.255")).isTrue();
        assertThat(ClientAddress.isLoopback("::1")).isTrue();
        assertThat(ClientAddress.isLoopback("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(ClientAddress.isLoopback("[::1]")).isTrue();
        assertThat(ClientAddress.isLoopback("::1%1")).isTrue();
        assertThat(ClientAddress.isLoopback("::ffff:127.0.0.1")).isTrue();
        assertThat(ClientAddress.isLoopback("::ffff:127.1.2.3")).isTrue();
        assertThat(ClientAddress.isLoopback("0:0:0:0:0:ffff:127.0.0.1")).isTrue();

        assertThat(ClientAddress.isLoopback(null)).isFalse();
        assertThat(ClientAddress.isLoopback("")).isFalse();
        assertThat(ClientAddress.isLoopback("localhost")).isFalse();
        assertThat(ClientAddress.isLoopback("example.com")).isFalse();
        assertThat(ClientAddress.isLoopback("8.8.8.8")).isFalse();
        assertThat(ClientAddress.isLoopback("10.0.0.1")).isFalse();
        assertThat(ClientAddress.isLoopback("0.0.0.0")).isFalse();
        assertThat(ClientAddress.isLoopback("0.0.0.1")).isFalse();
        assertThat(ClientAddress.isLoopback("128.0.0.1")).isFalse();
        assertThat(ClientAddress.isLoopback("203.0.113.10")).isFalse();
        assertThat(ClientAddress.isLoopback("192.168.1.1")).isFalse();
        assertThat(ClientAddress.isLoopback("::ffff:8.8.8.8")).isFalse();
        assertThat(ClientAddress.isLoopback("fe80::1")).isFalse();
        assertThat(ClientAddress.isLoopback("::")).isFalse();
        assertThat(ClientAddress.isLoopback("127.0.0.1, 8.8.8.8")).isFalse();
        assertThat(ClientAddress.isLoopback("127.0.0.1.example.com")).isFalse();
        assertThat(ClientAddress.isLoopback("999.1.1.1")).isFalse();
        assertThat(ClientAddress.isLoopback("::::")).isFalse();
    }
}
