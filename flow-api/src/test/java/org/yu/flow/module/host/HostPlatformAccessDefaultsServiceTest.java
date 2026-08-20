package org.yu.flow.module.host;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.dto.HostPlatformAccessDefaultsDTO;
import org.yu.flow.module.sysconfig.service.SysConfigService;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HostPlatformAccessDefaultsServiceTest {

    private YuFlowRuntimeSettings runtime;
    private SysConfigService sysConfigService;
    private HostPrincipalSettingsStore principalStore;
    private HostPlatformAccessDefaultsService service;

    @BeforeEach
    void setUp() throws Exception {
        runtime = mock(YuFlowRuntimeSettings.class);
        sysConfigService = mock(SysConfigService.class);
        principalStore = mock(HostPrincipalSettingsStore.class);
        service = new HostPlatformAccessDefaultsService();
        setField(service, "runtimeSettings", runtime);
        setField(service, "sysConfigService", sysConfigService);
        setField(service, "principalSettingsStore", principalStore);

        when(runtime.getIngressDefaultAuthMode()).thenReturn("HOST");
        when(runtime.isIngressDefaultRateLimitEnabled()).thenReturn(true);
        when(runtime.getIngressDefaultRateLimitQps()).thenReturn(80);
        when(runtime.getIngressDefaultIpAllowlist()).thenReturn("10.0.0.0/8");
        when(runtime.getIngressDefaultTimeoutMs()).thenReturn(15000);
        when(principalStore.load()).thenReturn(new HostPrincipalSettings());
        when(principalStore.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void load_readsRuntimeAndPrincipal() {
        HostPrincipalSettings principal = new HostPrincipalSettings();
        principal.setIngressCallerEnabled(true);
        CallerAccessRule rule = new CallerAccessRule();
        rule.setName("运营");
        principal.setIngressRules(List.of(rule));
        when(principalStore.load()).thenReturn(principal);

        HostPlatformAccessDefaultsDTO dto = service.load();
        assertEquals("HOST", dto.getAuthMode());
        assertTrue(dto.getRateLimitEnabled());
        assertEquals(80, dto.getRateLimitQps());
        assertEquals("10.0.0.0/8", dto.getIpAllowlist());
        assertEquals(15000, dto.getTimeoutMs());
        assertTrue(dto.getIngressCallerEnabled());
        assertEquals(1, dto.getIngressRules().size());
    }

    @Test
    void save_rejectsNoneWithCallerPolicy() {
        HostPlatformAccessDefaultsDTO body = new HostPlatformAccessDefaultsDTO()
                .setAuthMode("NONE")
                .setIngressCallerEnabled(true);
        FlowException ex = assertThrows(FlowException.class, () -> service.save(body));
        assertEquals("PLATFORM_ACCESS_NONE_CALLER", ex.getErrorCode());
    }

    @Test
    void save_writesIngressKeysAndMergesPrincipal() {
        HostPrincipalSettings existing = new HostPrincipalSettings();
        existing.setEnabled(true);
        existing.setMode(HostPrincipalSettings.MODE_API);
        existing.setAdminUserTypes(List.of("ADMIN"));
        when(principalStore.load()).thenReturn(existing);

        CallerAccessRule rule = new CallerAccessRule();
        rule.setName("已登录");
        PrivacyAccessRule privacy = new PrivacyAccessRule();
        privacy.setName("脱敏");
        HostPlatformAccessDefaultsDTO body = new HostPlatformAccessDefaultsDTO()
                .setAuthMode("host")
                .setRateLimitEnabled(false)
                .setRateLimitQps(50)
                .setIpAllowlist("")
                .setTimeoutMs(0)
                .setIngressCallerEnabled(true)
                .setIngressRules(List.of(rule))
                .setPrivacyRules(List.of(privacy))
                .setPrivacyProfileId("builtin")
                .setPrivacyFieldSuffix("_sec")
                .setPrivacyExtraFields(List.of("mobile"))
                .setPrivacyStripSuffix(false);

        HostPlatformAccessDefaultsDTO savedDto = service.save(body);
        assertEquals("HOST", savedDto.getAuthMode());
        assertFalse(savedDto.getRateLimitEnabled());
        assertEquals(50, savedDto.getRateLimitQps());
        assertEquals("", savedDto.getIpAllowlist());
        assertEquals(0, savedDto.getTimeoutMs());
        assertTrue(savedDto.getIngressCallerEnabled());

        verify(sysConfigService).updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_AUTH_MODE, "HOST");
        verify(sysConfigService).updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_RATE_LIMIT_ENABLED, "false");
        verify(sysConfigService).updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_RATE_LIMIT_QPS, "50");
        verify(sysConfigService).updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_IP_ALLOWLIST, "");
        verify(sysConfigService).updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_TIMEOUT_MS, "0");

        ArgumentCaptor<HostPrincipalSettings> captor =
                ArgumentCaptor.forClass(HostPrincipalSettings.class);
        verify(principalStore).save(captor.capture());
        HostPrincipalSettings saved = captor.getValue();
        assertTrue(saved.isEnabled());
        assertEquals(List.of("ADMIN"), saved.getAdminUserTypes());
        assertTrue(saved.isIngressCallerEnabled());
        assertEquals(1, saved.getIngressRules().size());
        assertEquals(1, saved.getPrivacyRules().size());
        assertEquals("builtin", saved.getPrivacyProfileId());
        assertEquals("_sec", saved.getPrivacyFieldSuffix());
        assertEquals(List.of("mobile"), saved.getPrivacyExtraFields());
        assertFalse(saved.getPrivacyStripSuffix());
        assertTrue(saved.getPrivacyRevealRoles() == null || saved.getPrivacyRevealRoles().isEmpty());
    }

    @Test
    void save_invalidAuthMode() {
        HostPlatformAccessDefaultsDTO body = new HostPlatformAccessDefaultsDTO()
                .setAuthMode("JWT");
        FlowException ex = assertThrows(FlowException.class, () -> service.save(body));
        assertEquals("PLATFORM_ACCESS_AUTH_MODE", ex.getErrorCode());
        assertFalse(ex.getMessage().isBlank());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
