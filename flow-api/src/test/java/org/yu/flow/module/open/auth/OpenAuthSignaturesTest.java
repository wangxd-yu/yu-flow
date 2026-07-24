package org.yu.flow.module.open.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAuthSignaturesTest {

    @Test
    void buildPayload_canonicalForm() {
        String payload = OpenAuthSignatures.buildPayload(
                "post", "/demo/hello", "1710000000", "nonce1", "abc");
        assertEquals("POST\n/demo/hello\n1710000000\nnonce1\nabc", payload);
    }

    @Test
    void buildPayload_emptyBodyHash() {
        String payload = OpenAuthSignatures.buildPayload(
                "GET", "/x", "1", "n", "");
        assertEquals("GET\n/x\n1\nn\n", payload);
    }

    @Test
    void sign_isDeterministicAndHex() {
        String a = OpenAuthSignatures.sign("secret", "GET", "/p", "100", "n1", "");
        String b = OpenAuthSignatures.sign("secret", "GET", "/p", "100", "n1", "");
        assertEquals(a, b);
        assertEquals(64, a.length());
        assertTrue(a.matches("[0-9a-f]+"));
    }

    @Test
    void sign_changesWithAnyField() {
        String base = OpenAuthSignatures.sign("secret", "GET", "/p", "100", "n1", "");
        assertNotEquals(base, OpenAuthSignatures.sign("secret", "POST", "/p", "100", "n1", ""));
        assertNotEquals(base, OpenAuthSignatures.sign("secret", "GET", "/q", "100", "n1", ""));
        assertNotEquals(base, OpenAuthSignatures.sign("secret", "GET", "/p", "101", "n1", ""));
        assertNotEquals(base, OpenAuthSignatures.sign("secret", "GET", "/p", "100", "n2", ""));
        assertNotEquals(base, OpenAuthSignatures.sign("secret", "GET", "/p", "100", "n1", "deadbeef"));
        assertNotEquals(base, OpenAuthSignatures.sign("other", "GET", "/p", "100", "n1", ""));
    }
}
