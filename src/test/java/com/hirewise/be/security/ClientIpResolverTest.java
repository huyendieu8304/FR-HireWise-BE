package com.hirewise.be.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-39: {@code offer_signatures.ip_address} is written inside the signing
 * transaction, so anything that cannot be stored safely has to be rejected
 * here rather than blowing up the candidate's job acceptance.
 */
class ClientIpResolverTest {

    @Test
    void acceptsIpv4() {
        assertThat(ClientIpResolver.toIpLiteral("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void acceptsIpv6() {
        assertThat(ClientIpResolver.toIpLiteral("2001:db8::1")).isEqualTo("2001:db8::1");
        assertThat(ClientIpResolver.toIpLiteral("::1")).isEqualTo("::1");
    }

    @Test
    void acceptsTheLongestPossibleLiteral() {
        String longest = "0000:0000:0000:0000:0000:ffff:255.255.255.255";

        assertThat(longest).hasSize(45);
        assertThat(ClientIpResolver.toIpLiteral(longest)).isEqualTo(longest);
    }

    @Test
    void stripsAPortFromIpv4() {
        assertThat(ClientIpResolver.toIpLiteral("203.0.113.7:5678")).isEqualTo("203.0.113.7");
    }

    @Test
    void stripsBracketsAndPortFromIpv6() {
        assertThat(ClientIpResolver.toIpLiteral("[2001:db8::1]:8080")).isEqualTo("2001:db8::1");
        assertThat(ClientIpResolver.toIpLiteral("[::1]")).isEqualTo("::1");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(ClientIpResolver.toIpLiteral("  203.0.113.7  ")).isEqualTo("203.0.113.7");
    }

    @Test
    void rejectsAnOctetAbove255() {
        assertThat(ClientIpResolver.toIpLiteral("999.0.113.7")).isNull();
    }

    @Test
    void rejectsAHostnameRatherThanResolvingIt() {
        // Must not become a DNS lookup on the signing path.
        assertThat(ClientIpResolver.toIpLiteral("evil.example.com")).isNull();
    }

    @Test
    void rejectsArbitraryHeaderJunk() {
        assertThat(ClientIpResolver.toIpLiteral("unknown")).isNull();
        assertThat(ClientIpResolver.toIpLiteral("<script>alert(1)</script>")).isNull();
        assertThat(ClientIpResolver.toIpLiteral("'; DROP TABLE offers; --")).isNull();
    }

    @Test
    void rejectsAnOverlongValueThatWouldOverflowTheColumn() {
        assertThat(ClientIpResolver.toIpLiteral("1".repeat(500))).isNull();
        assertThat(ClientIpResolver.toIpLiteral("2001:db8::" + "a".repeat(100))).isNull();
    }

    @Test
    void rejectsNullAndBlank() {
        assertThat(ClientIpResolver.toIpLiteral(null)).isNull();
        assertThat(ClientIpResolver.toIpLiteral("   ")).isNull();
        assertThat(ClientIpResolver.toIpLiteral("[]")).isNull();
    }

    @Test
    void rejectsAnUnclosedBracket() {
        assertThat(ClientIpResolver.toIpLiteral("[2001:db8::1")).isNull();
    }
}
