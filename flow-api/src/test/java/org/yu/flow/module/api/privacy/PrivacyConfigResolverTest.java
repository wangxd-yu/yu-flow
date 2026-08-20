package org.yu.flow.module.api.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.host.HostPrincipalSettings;
import org.yu.flow.module.host.HostPrincipalSettingsStore;
import org.yu.flow.module.host.HostPrivacyProfile;
import org.yu.flow.module.host.HostPrivacyProfilesStore;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivacyConfigResolverTest {

    private FlowDirectoryService directories;
    private HostPrivacyProfilesStore profiles;
    private HostPrincipalSettingsStore principalStore;
    private PrivacyConfigResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        directories = mock(FlowDirectoryService.class);
        profiles = mock(HostPrivacyProfilesStore.class);
        principalStore = mock(HostPrincipalSettingsStore.class);
        resolver = new PrivacyConfigResolver();
        setField(resolver, "flowDirectoryService", directories);
        setField(resolver, "hostPrivacyProfilesStore", profiles);
        setField(resolver, "hostPrincipalSettingsStore", principalStore);
        when(principalStore.load()).thenReturn(new HostPrincipalSettings());
    }

    @Test
    void blankApiAndDir_usesHostDefaultProfile() {
        HostPrincipalSettings host = new HostPrincipalSettings();
        host.setPrivacyProfileId("staff-sm4");
        when(principalStore.load()).thenReturn(host);
        HostPrivacyProfile profile = new HostPrivacyProfile();
        profile.setId("staff-sm4");
        profile.setDecryptAlg("AES");
        profile.setDecryptKey("abcdefghijklmnop");
        when(profiles.find("staff-sm4")).thenReturn(profile);

        FlowApiDO api = new FlowApiDO();
        api.setDirectoryId("dir-1");
        when(directories.resolveDirectoryPrivacyOverrides("dir-1")).thenReturn(null);

        EffectivePrivacy eff = resolver.resolve(api, true);
        assertEquals("staff-sm4", eff.getProfileId());
        assertEquals("AES", eff.getDecryptAlg());
    }

    @Test
    void apiProfileWinsOverHostDefault() {
        HostPrincipalSettings host = new HostPrincipalSettings();
        host.setPrivacyProfileId("staff-sm4");
        when(principalStore.load()).thenReturn(host);

        FlowApiDO api = new FlowApiDO();
        api.setPrivacyConfig("{\"profileId\":\"api-aes\"}");
        HostPrivacyProfile profile = new HostPrivacyProfile();
        profile.setId("api-aes");
        profile.setDecryptAlg("AES");
        when(profiles.find("api-aes")).thenReturn(profile);

        EffectivePrivacy eff = resolver.resolve(api, true);
        assertEquals("api-aes", eff.getProfileId());
    }

    @Test
    void hostExtraFieldsFillWhenApiOmitsThem() {
        HostPrincipalSettings host = new HostPrincipalSettings();
        host.setPrivacyExtraFields(List.of("mobile"));
        when(principalStore.load()).thenReturn(host);

        FlowApiDO api = new FlowApiDO();
        api.setPrivacyConfig("{\"enabled\":true}");

        EffectivePrivacy eff = resolver.resolve(api, true);
        assertTrue(eff.isEnabled());
        assertTrue(eff.isPrivacyField("mobile"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
