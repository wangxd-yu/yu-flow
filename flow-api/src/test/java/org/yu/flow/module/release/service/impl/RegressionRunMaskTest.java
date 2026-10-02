package org.yu.flow.module.release.service.impl;

import org.junit.jupiter.api.Test;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.util.SecretMasker;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegressionRunMaskTest {

    @Test
    void storedRegressionTextReplacesSecretsCollectedOnTheTrace() throws Exception {
        Method maskOf = ReleaseManageServiceImpl.class.getDeclaredMethod("maskOf", FlowTrace.class, String.class);
        maskOf.setAccessible(true);
        FlowTrace trace = new FlowTrace().setSecretValues(Set.of("super-secret-value"));

        String masked = (String) maskOf.invoke(null, trace, "实际=super-secret-value");

        assertEquals("实际=" + SecretMasker.MASK, masked);
        assertFalse(masked.contains("super-secret-value"));
    }
}
