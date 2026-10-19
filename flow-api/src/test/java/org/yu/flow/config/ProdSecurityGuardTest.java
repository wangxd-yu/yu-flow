package org.yu.flow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings.Keys;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProdSecurityGuardTest {

    private YuFlowProperties properties;
    private YuFlowRuntimeSettings runtime;
    private ProdSecurityGuard guard;

    @BeforeEach
    void setUp() {
        properties = new YuFlowProperties();
        properties.getRelease().setCurrentEnv("prod");
        properties.getRelease().setSigningKey("k");
        properties.getRelease().setLockAssetEditing(true);
        properties.getSecurity().setAllowIgnoreSsl(false);
        properties.getSecurity().setScriptAllowedLanguages(List.of("aviator", "javascript"));
        runtime = mock(YuFlowRuntimeSettings.class);
        when(runtime.isOpenNonceFailClosed()).thenReturn(true);
        when(runtime.getScriptAllowedLanguages()).thenReturn(List.of("aviator", "javascript"));
        when(runtime.getMqLogPayloadMode()).thenReturn("MASK");
        guard = new ProdSecurityGuard(properties, runtime, new MockEnvironment());
    }

    @Test
    void secureProdStarts() {
        assertTrue(guard.findings().isEmpty());
        assertDoesNotThrow(guard::afterSingletonsInstantiated);
    }

    @Test
    void anonymousApisBlockStartupUnlessExplicitlyAllowed() {
        properties.getSecurity().setAllowIngressAuthNone(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::afterSingletonsInstantiated);
        assertTrue(ex.getMessage().contains("YU_FLOW_ALLOW_INGRESS_AUTH_NONE=false"));

        properties.getSecurity().setAllowInsecureProd(true);
        assertDoesNotThrow(guard::afterSingletonsInstantiated);
    }

    @Test
    void failOpenRateLimitIsOnlyAWarning() {
        when(runtime.isIngressRateLimitFailOpen()).thenReturn(true);

        List<ProdSecurityGuard.Finding> findings = guard.findings();
        assertEquals(1, findings.size());
        assertTrue(!findings.get(0).blocking());
        assertDoesNotThrow(guard::afterSingletonsInstantiated);
    }

    @Test
    void nonProdIsNotChecked() {
        properties.getRelease().setCurrentEnv("DEV");
        properties.getSecurity().setAllowIgnoreSsl(true);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);
        assertNull(guard.sysConfigRejectReason(Keys.OPEN_ALLOW_PLAIN_SECRET, "true", 1));
    }

    @Test
    void prodRejectsWeakeningOpenPlatformThroughSysConfig() {
        assertThrows(FlowException.class,
                () -> guard.assertSysConfigAllowed(Keys.OPEN_NONCE_FAIL_CLOSED, "false", 1));
        assertNull(guard.sysConfigRejectReason(Keys.OPEN_NONCE_FAIL_CLOSED, "false", 0));
        assertNull(guard.sysConfigRejectReason(Keys.OPEN_ALLOW_PLAIN_SECRET, "false", 1));
        assertNull(guard.sysConfigRejectReason("SYSTEM_PREFIX", "/flow-api", 1));
    }
}
