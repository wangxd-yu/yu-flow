package org.yu.flow.module.api.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.HostPrincipalSettings;
import org.yu.flow.module.host.HostPrincipalSettingsStore;
import org.yu.flow.module.rbac.service.RbacService;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivacyRoleMatcherTest {

    private PrivacyRoleMatcher matcher;
    private HostPrincipalSettingsStore store;
    private RbacService rbacService;

    @BeforeEach
    void setUp() throws Exception {
        matcher = new PrivacyRoleMatcher();
        store = mock(HostPrincipalSettingsStore.class);
        rbacService = mock(RbacService.class);
        setField(matcher, "hostPrincipalSettingsStore", store);
        setField(matcher, "rbacService", rbacService);
    }

    @Test
    void revealRole_caseInsensitive_beatsMask() {
        HostPrincipalSettings settings = new HostPrincipalSettings();
        settings.setPrivacyRevealRoles(List.of("PrivacyAdmin"));
        settings.setPrivacyMaskRoles(List.of("PrivacyAdmin", "user"));
        when(store.load()).thenReturn(settings);

        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("u1")
                .username("u1")
                .roles(Set.of("privacyadmin"))
                .authChannel("HOST_RESOLVER_API")
                .build();
        assertEquals(PrivacyClass.REVEAL, matcher.resolve(principal));
    }

    @Test
    void loggedInWithoutHit_masks() {
        HostPrincipalSettings settings = new HostPrincipalSettings();
        settings.setPrivacyRevealRoles(List.of("priv-admin"));
        settings.setPrivacyMaskRoles(List.of("user"));
        when(store.load()).thenReturn(settings);

        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("u1")
                .username("u1")
                .roles(Set.of("other"))
                .authChannel("HOST_RESOLVER_API")
                .build();
        assertEquals(PrivacyClass.MASK, matcher.resolve(principal));
    }

    @Test
    void openApp_alwaysMask() {
        HostPrincipalSettings settings = new HostPrincipalSettings();
        settings.setPrivacyRevealRoles(List.of("any"));
        when(store.load()).thenReturn(settings);
        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("open:app1")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .roles(Set.of("any"))
                .authChannel("OPEN_APP")
                .build();
        assertEquals(PrivacyClass.MASK, matcher.resolve(principal));
    }

    @Test
    void flowJwt_revealPerm_escapeHatch() {
        when(store.load()).thenReturn(new HostPrincipalSettings());
        when(rbacService.hasAnyPerm("admin", PrivacyRoleMatcher.FLOW_PERM_REVEAL, "*")).thenReturn(true);
        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("admin")
                .username("admin")
                .authChannel(PrivacyRoleMatcher.CHANNEL_FLOW_JWT)
                .build();
        assertEquals(PrivacyClass.REVEAL, matcher.resolve(principal));
    }

    @Test
    void anonymous_masks() {
        assertEquals(PrivacyClass.MASK, matcher.resolve(null));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
