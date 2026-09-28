package org.yu.flow.util;

import org.junit.jupiter.api.Test;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretMaskerTest {

    @Test
    void masksRawAndJsonEscapedForms() {
        String secret = "p\"ss\\word";
        String json = "{\"header\":\"Bearer p\\\"ss\\\\word\"}";
        String masked = SecretMasker.mask(json, Set.of(secret));
        assertEquals("{\"header\":\"Bearer ******\"}", masked);
        assertEquals("curl -H 'k: ******'", SecretMasker.mask("curl -H 'k: " + secret + "'", Set.of(secret)));
    }

    @Test
    void longerSecretReplacedFirst() {
        String masked = SecretMasker.mask("token=abcdef123", List.of("abcd", "abcdef123"));
        assertEquals("token=******", masked);
    }

    @Test
    void shortValuesAreIgnored() {
        String text = "status=on";
        assertSame(text, SecretMasker.mask(text, Set.of("on")));
    }

    @Test
    void noSecretsReturnsInput() {
        String text = "anything";
        assertSame(text, SecretMasker.mask(text, Set.of()));
        assertFalse(SecretMasker.mask("abc secret1 abc", Set.of("secret1")).contains("secret1"));
    }

    @Test
    void maskCopyReturnsMaskedCopyOfTrace() {
        FlowTrace trace = new FlowTrace().setStatus("success")
                .setGlobalOutputs(Map.of("echo", "Bearer tok-12345"))
                .setStepLogs(List.of(new ExecutionLog().setNodeId("http").setOutputs(Map.of("header", "tok-12345"))));
        trace.setSecretValues(Set.of("tok-12345"));

        FlowTrace masked = SecretMasker.maskCopy(trace, trace.getSecretValues());

        assertNotSame(trace, masked);
        assertEquals(Map.of("echo", "Bearer ******"), masked.getGlobalOutputs());
        assertEquals(Map.of("header", "******"), masked.getStepLogs().get(0).getOutputs());
        assertEquals(Map.of("echo", "Bearer tok-12345"), trace.getGlobalOutputs());
        assertSame(trace, SecretMasker.maskCopy(trace, Set.of("not-there")));
    }

    @Test
    void nestedScopesShareOneSetAndOuterCleansUp() {
        try (SecretScope outer = SecretScope.open()) {
            try (SecretScope inner = SecretScope.open()) {
                SecretScope.currentOrNew().add("nested-secret");
                assertSame(outer.values(), inner.values());
            }
            assertTrue(outer.values().contains("nested-secret"));
            assertEquals("key=******", outer.mask("key=nested-secret"));
        }
        assertFalse(SecretScope.currentOrNew().contains("nested-secret"));
    }
}
