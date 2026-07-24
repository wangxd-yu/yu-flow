package org.yu.flow.module.sysconfig.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YuFlowRuntimeSettingsTest {

    private YuFlowProperties props;
    private SysConfigCacheManager cache;
    private YuFlowRuntimeSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        props = new YuFlowProperties();
        props.getOpen().setEnabled(true);
        props.getOpen().setAllowPlainSecret(false);
        props.getIngress().setEnabled(false);
        props.getIngress().setDefaultAuthMode("NONE");

        cache = mock(SysConfigCacheManager.class);
        when(cache.getConfig(anyString())).thenReturn(Optional.empty());

        settings = new YuFlowRuntimeSettings();
        setField(settings, "yuFlowProperties", props);
        setField(settings, "sysConfigCacheManager", cache);
    }

    @Test
    void missingSysConfig_fallsBackToYml() {
        assertTrue(settings.isOpenEnabled());
        assertFalse(settings.isOpenAllowPlainSecret());
        assertFalse(settings.isIngressEnabled());
        assertEquals("NONE", settings.getIngressDefaultAuthMode());
    }

    @Test
    void presentSysConfig_overridesYml() {
        when(cache.getConfig(YuFlowRuntimeSettings.Keys.INGRESS_ENABLED))
                .thenReturn(Optional.of(cfg(YuFlowRuntimeSettings.Keys.INGRESS_ENABLED, "true")));
        when(cache.getConfig(YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_AUTH_MODE))
                .thenReturn(Optional.of(cfg(YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_AUTH_MODE, "HOST")));

        assertTrue(settings.isIngressEnabled());
        assertEquals("HOST", settings.getIngressDefaultAuthMode());
        // 未覆盖的仍走 yml
        assertTrue(settings.isOpenEnabled());
    }

    @Test
    void entryPrefix_alwaysFromYml() {
        props.getOpen().setEntryPrefix("/custom/open");
        assertEquals("/custom/open", settings.getOpenEntryPrefix());
    }

    private static SysConfigDO cfg(String key, String value) {
        return SysConfigDO.builder().configKey(key).configValue(value).status(1).build();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
