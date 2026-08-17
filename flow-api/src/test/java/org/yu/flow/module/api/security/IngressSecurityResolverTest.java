package org.yu.flow.module.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import java.lang.reflect.Field;
import java.util.List;

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
        // 无目录服务时目录层为空，行为与「仅接口→全局」一致
        setField(resolver, "flowDirectoryService", null);
    }

    @Test
    void directoryLayer_betweenApiAndGlobal() throws Exception {
        org.yu.flow.module.directory.service.FlowDirectoryService dirSvc =
                org.mockito.Mockito.mock(org.yu.flow.module.directory.service.FlowDirectoryService.class);
        ApiSecurityConfig dirCfg = new ApiSecurityConfig();
        dirCfg.setRateLimitEnabled(true);
        dirCfg.setRateLimitQps(5);
        dirCfg.setIpAllowlist("1.1.1.1");
        org.yu.flow.module.host.CallerPolicy dirPolicy = new org.yu.flow.module.host.CallerPolicy();
        dirPolicy.setEnabled(true);
        dirPolicy.getRoles().add("admin");
        dirCfg.setCallerPolicy(dirPolicy);
        org.mockito.Mockito.when(dirSvc.resolveDirectorySecurityOverrides("d1")).thenReturn(dirCfg);
        setField(resolver, "flowDirectoryService", dirSvc);

        FlowApiDO api = new FlowApiDO();
        api.setDirectoryId("d1");
        api.setSecurityConfig("{\"authMode\":\"INHERIT\"}");
        EffectiveSecurity sec = resolver.resolve(api);
        assertEquals(IngressAuthMode.HOST, sec.getAuthMode());
        assertTrue(sec.isRateLimitEnabled());
        assertEquals(5, sec.getRateLimitQps());
        assertEquals("1.1.1.1", sec.getIpAllowlist());
        assertNotNull(sec.getCallerPolicy());
        assertTrue(sec.getCallerPolicy().isEnabled());
        assertEquals(List.of("admin"), sec.getCallerPolicy().getRoles());
    }

    @Test
    void apiEnabledCallerPolicy_winsOverDirectory() throws Exception {
        org.yu.flow.module.directory.service.FlowDirectoryService dirSvc =
                org.mockito.Mockito.mock(org.yu.flow.module.directory.service.FlowDirectoryService.class);
        ApiSecurityConfig dirCfg = new ApiSecurityConfig();
        org.yu.flow.module.host.CallerPolicy dirPolicy = new org.yu.flow.module.host.CallerPolicy();
        dirPolicy.setEnabled(true);
        dirPolicy.getRoles().add("from-dir");
        dirCfg.setCallerPolicy(dirPolicy);
        org.mockito.Mockito.when(dirSvc.resolveDirectorySecurityOverrides("d1")).thenReturn(dirCfg);
        setField(resolver, "flowDirectoryService", dirSvc);

        FlowApiDO api = new FlowApiDO();
        api.setDirectoryId("d1");
        api.setSecurityConfig("{\"authMode\":\"HOST\",\"callerPolicy\":{\"enabled\":true,\"roles\":[\"from-api\"]}}");
        EffectiveSecurity sec = resolver.resolve(api);
        assertEquals(List.of("from-api"), sec.getCallerPolicy().getRoles());
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
