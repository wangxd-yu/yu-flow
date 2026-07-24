package org.yu.flow.module.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class IngressSecurityResolverTest {

    private YuFlowProperties props;
    private YuFlowRuntimeSettings runtimeSettings;
    private IngressSecurityResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        props = new YuFlowProperties();
        props.getIngress().setEnabled(true);
        props.getIngress().setDefaultAuthMode("HOST");
        props.getIngress().setDefaultAntiReplay(true);
        props.getIngress().setDefaultRateLimitEnabled(false);
        props.getIngress().setDefaultRateLimitQps(100);
        props.getIngress().setDefaultIpAllowlist("10.0.0.1");

        runtimeSettings = new YuFlowRuntimeSettings();
        setField(runtimeSettings, "yuFlowProperties", props);
        // sysConfigCacheManager 为 null → 全部回退 yml
        setField(runtimeSettings, "sysConfigCacheManager", null);

        resolver = new IngressSecurityResolver();
        setField(resolver, "yuFlowRuntimeSettings", runtimeSettings);
    }

    @Test
    void disabled_trustsHost() {
        props.getIngress().setEnabled(false);
        FlowApiDO api = new FlowApiDO();
        api.setSecurityConfig("{\"authMode\":\"OPEN\"}");
        EffectiveSecurity sec = resolver.resolve(api);
        assertEquals(IngressAuthMode.NONE, sec.getAuthMode());
        assertFalse(sec.isRateLimitEnabled());
        assertEquals("", sec.getIpAllowlist());
    }

    @Test
    void inherit_usesGlobalDefaults() {
        FlowApiDO api = new FlowApiDO();
        api.setSecurityConfig("{\"authMode\":\"INHERIT\"}");
        EffectiveSecurity sec = resolver.resolve(api);
        assertEquals(IngressAuthMode.HOST, sec.getAuthMode());
        assertTrue(sec.isAntiReplay());
        assertFalse(sec.isRateLimitEnabled());
        assertEquals("10.0.0.1", sec.getIpAllowlist());
    }

    @Test
    void override_winsPerField() {
        FlowApiDO api = new FlowApiDO();
        api.setSecurityConfig("{\"authMode\":\"NONE\",\"rateLimitEnabled\":true,\"rateLimitQps\":1,\"ipAllowlist\":\"\"}");
        EffectiveSecurity sec = resolver.resolve(api);
        assertEquals(IngressAuthMode.NONE, sec.getAuthMode());
        assertTrue(sec.isRateLimitEnabled());
        assertEquals(1, sec.getRateLimitQps());
        assertEquals("", sec.getIpAllowlist());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
