package com.zhyq.park.marketing.password;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

class ClientAddressResolverTest {
    private final ClientAddressResolver resolver = new ClientAddressResolver("172.24.0.5/32,172.24.0.2/32");

    @Test void defaultsToSocketPeerAndIgnoresUntrustedForwardedHeaders() {
        MockHttpServletRequest request = request("198.51.100.10", "203.0.113.100, 172.24.0.2");
        request.addHeader("X-Real-IP", "203.0.113.101");
        request.addHeader("Forwarded", "for=203.0.113.102");
        assertThat(new ClientAddressResolver("").resolve(request)).isEqualTo("198.51.100.10");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test void twoVerifiedProxyHopsResolveClientAndIgnoreForgedLeftmostAddress() {
        assertThat(resolver.resolve(request("172.24.0.5", "192.0.2.200, 198.51.100.10, 172.24.0.2")))
                .isEqualTo("198.51.100.10");
        assertThat(resolver.resolve(request("172.24.0.5", "198.51.100.11, 172.24.0.2")))
                .isEqualTo("198.51.100.11");
    }

    @Test void stopsAtFirstUntrustedIntermediateHop() {
        assertThat(resolver.resolve(request("172.24.0.5", "192.0.2.200, 172.24.0.99"))).isEqualTo("172.24.0.99");
    }

    @Test void directNginxAccessCannotUseSpoofedLeftmostAddress() {
        assertThat(resolver.resolve(request("172.24.0.5", "192.0.2.200, 198.51.100.10"))).isEqualTo("198.51.100.10");
    }

    @Test void repeatedHeaderLinesPreserveProxyChainOrder() {
        MockHttpServletRequest request = request("172.24.0.5", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "172.24.0.2");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test void invalidOrOversizedChainsNeverBecomeClientKeys() {
        for (String header : new String[]{"", "example.com", "unknown", "198.51.100.10:1234", "999.1.1.1", "198.51.100.10,", "1.2", "a".repeat(2049), "198.51.100.10,".repeat(17)}) {
            assertThat(resolver.resolve(request("172.24.0.5", header))).isEqualTo("172.24.0.5");
        }
        MockHttpServletRequest noHeader = new MockHttpServletRequest(); noHeader.setRemoteAddr("172.24.0.5");
        assertThat(resolver.resolve(noHeader)).isEqualTo("172.24.0.5");
    }

    @Test void numericIpv6AddressesAreNormalizedAndSupportExplicitCidr() {
        ClientAddressResolver ipv6 = new ClientAddressResolver("fd00:1234::/64");
        assertThat(ipv6.resolve(request("fd00:1234::5", "2001:db8::123, fd00:1234::2")))
                .isEqualTo("2001:db8:0:0:0:0:0:123");
        assertThat(ipv6.resolve(request("2001:db8::10", "2001:db8::999")))
                .isEqualTo("2001:db8:0:0:0:0:0:10");
    }

    @Test void invalidTrustConfigurationFailsAtStartup() {
        for (String configured : new String[]{"proxy.internal", "0.0.0.0/0", "::/0", "172.24.0.5/33", "172.24.0.5/abc", "172.24.0.5,", "172.24.0.5/32/2"}) {
            assertThatThrownBy(() -> new ClientAddressResolver(configured)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void oneClientCannotConsumeAnotherClientsIpLimitBehindSameNginx() {
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter();
        String first = resolver.resolve(request("172.24.0.5", "198.51.100.10, 172.24.0.2"));
        String second = resolver.resolve(request("172.24.0.5", "198.51.100.11, 172.24.0.2"));
        limiter.check("register:ip:" + first, 1);
        assertThatThrownBy(() -> limiter.check("register:ip:" + first, 1)).hasMessageContaining("尝试次数过多");
        assertThatCode(() -> limiter.check("register:ip:" + second, 1)).doesNotThrowAnyException();
    }

    private MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer); request.addHeader("X-Forwarded-For", forwardedFor); return request;
    }
}
