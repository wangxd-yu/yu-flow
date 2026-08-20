package org.yu.flow.module.api.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.HostPrincipalSettings;
import org.yu.flow.module.host.HostPrincipalSettingsStore;
import org.yu.flow.module.host.PrincipalMatch;
import org.yu.flow.module.host.PrivacyAccessRule;
import org.yu.flow.module.rbac.service.RbacService;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void firstMatch_officerReveal_thenAnyMask() {
        PrivacyAccessRule officer = new PrivacyAccessRule();
        officer.setName("专员");
        officer.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        officer.setRoles(List.of("PRIVACY_OFFICER"));
        officer.setPrivacy(PrivacyAccessRule.PRIVACY_REVEAL);
        PrivacyAccessRule loggedIn = new PrivacyAccessRule();
        loggedIn.setName("已登录");
        loggedIn.setPrincipals(PrincipalMatch.PRINCIPALS_ANY);
        loggedIn.setPrivacy(PrivacyAccessRule.PRIVACY_MASK);
        List<PrivacyAccessRule> rules = List.of(officer, loggedIn);

        FlowHostPrincipal officerP = FlowHostPrincipal.builder()
                .userId("o1")
                .roles(Set.of("privacy_officer"))
                .authChannel("HOST_RESOLVER_API")
                .build();
        FlowHostPrincipal user = FlowHostPrincipal.builder()
                .userId("u1")
                .roles(Set.of("user"))
                .authChannel("HOST_RESOLVER_API")
                .build();
        assertEquals(PrivacyClass.REVEAL, matcher.resolveDecision(officerP, rules).getPrivacyClass());
        assertEquals(PrivacyClass.MASK, matcher.resolveDecision(user, rules).getPrivacyClass());
    }

    @Test
    void openApp_ignoresAnyAuthenticatedAndMatchRoles() {
        PrivacyAccessRule any = new PrivacyAccessRule();
        any.setPrincipals(PrincipalMatch.PRINCIPALS_ANY);
        any.setPrivacy(PrivacyAccessRule.PRIVACY_REVEAL);
        PrivacyAccessRule match = new PrivacyAccessRule();
        match.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        match.setRoles(List.of("any"));
        match.setPrivacy(PrivacyAccessRule.PRIVACY_REVEAL);
        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("open:app1")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .roles(Set.of("any"))
                .authChannel("OPEN_APP")
                .build();
        assertEquals(PrivacyClass.MASK, matcher.resolveDecision(principal, List.of(any, match)).getPrivacyClass());
    }

    @Test
    void fieldActions_boundToMatchedRule() {
        PrivacyAccessRule officer = new PrivacyAccessRule();
        officer.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        officer.setRoles(List.of("PRIVACY_OFFICER"));
        officer.setPrivacy(PrivacyAccessRule.PRIVACY_MASK);
        officer.setFields(java.util.Map.of("id_no", PrivacyAccessRule.FIELD_REVEAL, "mobile", PrivacyAccessRule.FIELD_DROP));
        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("o1")
                .roles(Set.of("PRIVACY_OFFICER"))
                .authChannel("HOST_RESOLVER_API")
                .build();
        PrivacyDecision d = matcher.resolveDecision(principal, List.of(officer));
        assertEquals(PrivacyClass.MASK, d.getPrivacyClass());
        assertEquals(PrivacyDecision.ACTION_REVEAL, d.actionFor("id_no", "id_no_encrypt"));
        assertTrue(d.drops("mobile", "mobile"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
