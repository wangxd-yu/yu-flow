package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.security.ApiSecurityConfigGuard;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.support.OssProfileCallerAuth;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallerPolicyMatcherTest {

    @Test
    void disabled_alwaysPass() {
        CallerPolicy p = new CallerPolicy();
        p.setEnabled(false);
        p.setUserTypes(List.of("ADMIN"));
        assertNull(CallerPolicyMatcher.denyReason(p, null));
    }

    @Test
    void enabled_requiresPrincipal() {
        CallerPolicy p = new CallerPolicy();
        p.setEnabled(true);
        assertNotNull(CallerPolicyMatcher.denyReason(p, null));
    }

    @Test
    void multiRow_allowOpenAppAndStaff() {
        CallerPolicy p = new CallerPolicy();
        p.setEnabled(true);
        CallerAccessRule staff = new CallerAccessRule();
        staff.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        staff.setUserTypes(List.of("STAFF"));
        CallerAccessRule open = new CallerAccessRule();
        open.setPrincipals(PrincipalMatch.PRINCIPALS_OPEN);
        p.setRules(List.of(staff, open));

        FlowHostPrincipal ops = FlowHostPrincipal.builder()
                .userId("s1")
                .userType("STAFF")
                .build();
        FlowHostPrincipal app = FlowHostPrincipal.builder()
                .userId("open:shop")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .build();
        FlowHostPrincipal user = FlowHostPrincipal.builder()
                .userId("u1")
                .userType("END_USER")
                .build();
        assertNull(CallerPolicyMatcher.denyReason(p, ops));
        assertNull(CallerPolicyMatcher.denyReason(p, app));
        assertNotNull(CallerPolicyMatcher.denyReason(p, user));
    }

    @Test
    void legacyBlock_stillWorksAsSingleMatchRule() {
        CallerPolicy p = new CallerPolicy();
        p.setEnabled(true);
        p.setMatch("ALL");
        p.setUserTypes(List.of("END_USER"));
        FlowHostPrincipal endUser = FlowHostPrincipal.builder()
                .userId("u1")
                .userType("END_USER")
                .build();
        FlowHostPrincipal admin = FlowHostPrincipal.builder()
                .userId("a1")
                .userType("ADMIN")
                .build();
        assertNull(CallerPolicyMatcher.denyReason(p, endUser));
        assertNotNull(CallerPolicyMatcher.denyReason(p, admin));
    }

    @Test
    void rolesAndPerms_anyMatch() {
        CallerPolicy p = new CallerPolicy();
        p.setEnabled(true);
        p.setMatch("ANY");
        p.setRoles(List.of("vip"));
        p.setPermissions(List.of("order:read"));
        FlowHostPrincipal vip = FlowHostPrincipal.builder()
                .userId("u1")
                .userType("END_USER")
                .roles(Set.of("vip"))
                .build();
        FlowHostPrincipal reader = FlowHostPrincipal.builder()
                .userId("u2")
                .userType("END_USER")
                .permissions(Set.of("order:read"))
                .build();
        FlowHostPrincipal none = FlowHostPrincipal.builder()
                .userId("u3")
                .userType("END_USER")
                .build();
        assertNull(CallerPolicyMatcher.denyReason(p, vip));
        assertNull(CallerPolicyMatcher.denyReason(p, reader));
        assertNotNull(CallerPolicyMatcher.denyReason(p, none));
    }

    @Test
    void starPermission_passes() {
        CallerPolicy p = new CallerPolicy();
        p.setEnabled(true);
        p.setPermissions(List.of("order:read"));
        FlowHostPrincipal admin = FlowHostPrincipal.builder()
                .userId("a1")
                .userType("ADMIN")
                .permissions(Set.of("*"))
                .build();
        assertTrue(CallerPolicyMatcher.matches(p, admin));
    }

    @Test
    void guard_rejectsNoneWithCallerPolicy() {
        String json = "{\"authMode\":\"NONE\",\"callerPolicy\":{\"enabled\":true,\"userTypes\":[\"ADMIN\"]}}";
        FlowException ex = assertThrows(FlowException.class,
                () -> ApiSecurityConfigGuard.assertCallerPolicyAllowed(json));
        assertTrue(ex.getMessage().contains("NONE"));
    }

    @Test
    void ossSave_rejectsAnonymousUploadPerm() {
        OssProfileCallerAuth auth = new OssProfileCallerAuth();
        OssUploadProfileDO profile = new OssUploadProfileDO();
        profile.setRequireAuth(false);
        profile.setVisibility("PRIVATE");
        profile.setUploadPerm("flow:oss:upload");
        FlowException ex = assertThrows(FlowException.class, () -> auth.validateOnSave(profile));
        assertTrue(ex.getMessage().contains("匿名"));
    }

    @Test
    void ossSave_publicStripsDownloadScope() {
        OssProfileCallerAuth auth = new OssProfileCallerAuth();
        OssUploadProfileDO profile = new OssUploadProfileDO();
        profile.setRequireAuth(true);
        profile.setVisibility("PUBLIC");
        profile.setCallerPolicy("{\"rules\":[{\"principals\":\"ANY_AUTHENTICATED\",\"upload\":true,\"downloadScope\":\"ALL\"}]}");
        auth.validateOnSave(profile);
        assertTrue(profile.getCallerPolicy().contains("\"downloadScope\":\"OFF\"")
                || profile.getCallerPolicy().contains("\"downloadScope\": \"OFF\""));
    }
}
