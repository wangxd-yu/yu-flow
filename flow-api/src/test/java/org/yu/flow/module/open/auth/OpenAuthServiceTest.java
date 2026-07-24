package org.yu.flow.module.open.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 开放平台鉴权纯逻辑单测（不启 Spring / Redis）。
 */
class OpenAuthServiceTest {

    @Test
    void ipAllowed_exactMatch() {
        assertTrue(OpenAuthService.ipAllowed("1.2.3.4", List.of("1.2.3.4")));
        assertFalse(OpenAuthService.ipAllowed("1.2.3.5", List.of("1.2.3.4")));
    }

    @Test
    void ipAllowed_emptyAllowlistMeansNoRestriction_callerShouldSkip() {
        // assertIp 在空列表时直接 return；本方法对空列表视为不匹配
        assertFalse(OpenAuthService.ipAllowed("1.2.3.4", List.of()));
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.5, 10.0.0.0/8, true",
            "11.0.0.1, 10.0.0.0/8, false",
            "192.168.1.10, 192.168.1.0/24, true",
            "192.168.2.10, 192.168.1.0/24, false",
            "127.0.0.1, 127.0.0.1/32, true"
    })
    void ipv4InCidr(String ip, String cidr, boolean expect) {
        assertEquals(expect, OpenAuthService.ipv4InCidr(ip, cidr));
        assertEquals(expect, OpenAuthService.ipAllowed(ip, List.of(cidr)));
    }

    @Test
    void ipv4InCidr_invalidRule() {
        assertFalse(OpenAuthService.ipv4InCidr("1.2.3.4", "bad"));
        assertFalse(OpenAuthService.ipv4InCidr("1.2.3.4", "1.2.3.0/99"));
        assertFalse(OpenAuthService.ipv4InCidr("not-an-ip", "10.0.0.0/8"));
    }

    @Test
    void openAuthException_codes() {
        assertEquals("OPEN_AUTH_MISSING", OpenAuthException.missing().getCode());
        assertEquals(401, OpenAuthException.invalid().getHttpStatus());
        assertEquals("OPEN_AUTH_IP_DENIED", OpenAuthException.ipDenied().getCode());
        assertEquals(429, OpenAuthException.rateLimited().getHttpStatus());
        assertEquals("OPEN_RATE_LIMITED", OpenAuthException.rateLimited().getCode());
        assertEquals("OPEN_AUTH_METHOD_DENIED", OpenAuthException.methodDenied("x").getCode());
        assertEquals("OPEN_HOST_AUTH_REQUIRED", OpenAuthException.hostAuthRequired().getCode());
        assertEquals(401, OpenAuthException.hostAuthRequired().getHttpStatus());
    }

    @Test
    void methodAllowed_blankMeansUnrestricted() {
        assertTrue(OpenAuthService.methodAllowed("GET", null));
        assertTrue(OpenAuthService.methodAllowed("POST", ""));
        assertTrue(OpenAuthService.methodAllowed("GET", "GET,POST"));
        assertTrue(OpenAuthService.methodAllowed("post", "GET,POST"));
        assertFalse(OpenAuthService.methodAllowed("DELETE", "GET,POST"));
    }
}
