package org.yu.flow.module.oss.support;

import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.rbac.service.RbacService;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OssProfileCallerAuthTest {

    @Test
    void openApp_mustMatchAccessRule() throws Exception {
        OssProfileCallerAuth auth = newAuth();
        OssUploadProfileDO profile = baseProfile();
        profile.setCallerPolicy("{\"rules\":[{\"principals\":\"MATCH\",\"userTypes\":[\"END_USER\"],"
                + "\"upload\":true,\"downloadScope\":\"SELF\"}]}");
        FlowHostPrincipal open = FlowHostPrincipal.builder()
                .userId("open:app")
                .username("app")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .build();

        assertThrows(FlowException.class, () -> auth.assertUpload(profile, open));

        profile.setCallerPolicy("{\"rules\":[{\"principals\":\"MATCH\",\"userTypes\":[\"OPEN_APP\"],"
                + "\"upload\":true,\"downloadScope\":\"OFF\"}]}");
        assertDoesNotThrow(() -> auth.assertUpload(profile, open));
    }

    @Test
    void openApp_requiresExplicitRule() throws Exception {
        OssProfileCallerAuth auth = newAuth();
        OssUploadProfileDO profile = baseProfile();
        profile.setCallerPolicy("{\"rules\":[{\"principals\":\"ANY_AUTHENTICATED\","
                + "\"upload\":true,\"downloadScope\":\"SELF\"}]}");
        FlowHostPrincipal open = FlowHostPrincipal.builder()
                .userId("open:app")
                .username("app")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .build();

        assertThrows(FlowException.class, () -> auth.assertUpload(profile, open));
    }

    @Test
    void openApp_principals_allowsUpload() throws Exception {
        OssProfileCallerAuth auth = newAuth();
        OssUploadProfileDO profile = baseProfile();
        profile.setCallerPolicy("{\"rules\":[{\"principals\":\"OPEN_APP\","
                + "\"upload\":true,\"downloadScope\":\"OFF\"}]}");
        FlowHostPrincipal open = FlowHostPrincipal.builder()
                .userId("open:app")
                .username("app")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .build();
        assertDoesNotThrow(() -> auth.assertUpload(profile, open));
    }

    @Test
    void matchRule_requiresIdentity() throws Exception {
        OssProfileCallerAuth auth = newAuth();
        OssUploadProfileDO profile = baseProfile();
        profile.setCallerPolicy("{\"rules\":[{\"principals\":\"MATCH\",\"upload\":true,\"downloadScope\":\"SELF\"}]}");

        assertThrows(FlowException.class, () -> auth.validateOnSave(profile));
    }

    @Test
    void anonymousUpload_cannotConfigureFlowPermission() throws Exception {
        OssProfileCallerAuth auth = newAuth();
        OssUploadProfileDO profile = baseProfile();
        profile.setRequireAuth(false);
        profile.setUploadPerm("flow:oss:upload");

        assertThrows(FlowException.class, () -> auth.validateOnSave(profile));
    }

    @Test
    void uploadPermission_supportsCommaSeparatedCodes() throws Exception {
        RbacService rbacService = mock(RbacService.class);
        when(rbacService.hasAnyPerm(eq("alice"), any(String[].class))).thenReturn(true);
        OssProfileCallerAuth auth = newAuth();
        setField(auth, "rbacService", rbacService);
        OssUploadProfileDO profile = baseProfile();
        profile.setUploadPerm("flow:oss:first, flow:oss:second");
        profile.setCallerPolicy("{\"rules\":[{\"principals\":\"ANY_AUTHENTICATED\","
                + "\"upload\":true,\"downloadScope\":\"SELF\"}]}");
        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("u1")
                .username("alice")
                .userType(FlowHostPrincipal.TYPE_ADMIN)
                .authChannel("FLOW_JWT")
                .build();

        assertDoesNotThrow(() -> auth.assertUpload(profile, principal));
    }

    private static OssProfileCallerAuth newAuth() throws Exception {
        OssProfileCallerAuth auth = new OssProfileCallerAuth();
        setField(auth, "rbacService", mock(RbacService.class));
        return auth;
    }

    private static OssUploadProfileDO baseProfile() {
        OssUploadProfileDO profile = new OssUploadProfileDO();
        profile.setCode("test");
        profile.setEnabled(true);
        profile.setRequireAuth(true);
        profile.setVisibility(OssUploadProfileDO.VISIBILITY_PRIVATE);
        return profile;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
