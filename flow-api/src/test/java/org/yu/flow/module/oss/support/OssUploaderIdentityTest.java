package org.yu.flow.module.oss.support;

import org.junit.jupiter.api.Test;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.dto.OssUploadOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssUploaderIdentityTest {

    @Test
    void fromPrincipal_normalizesUserType() {
        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("1")
                .username("admin")
                .userType("Admin")
                .deptId("0")
                .build();
        OssUploaderIdentity.Snapshot snap = OssUploaderIdentity.from(principal, null);
        assertEquals("1", snap.getUploadedBy());
        assertEquals("ADMIN", snap.getUploadedByUserType());
        assertEquals("admin", snap.getUploadedByName());
        assertEquals("0", snap.getDeptId());
    }

    @Test
    void override_canChangeUserType() {
        FlowHostPrincipal principal = FlowHostPrincipal.builder()
                .userId("admin-1")
                .userType("ADMIN")
                .build();
        OssUploadOptions options = new OssUploadOptions()
                .setUploadedByOverride("1001")
                .setUploadedByNameOverride("张三")
                .setUploadedByUserTypeOverride("end_user");
        OssUploaderIdentity.Snapshot snap = OssUploaderIdentity.from(principal, options);
        assertEquals("1001", snap.getUploadedBy());
        assertEquals("END_USER", snap.getUploadedByUserType());
        assertEquals("张三", snap.getUploadedByName());
    }

    @Test
    void isSelf_requiresUserIdAndUserType() {
        OssObjectDO object = new OssObjectDO();
        object.setUploadedBy("1");
        object.setUploadedByUserType("ADMIN");
        FlowHostPrincipal admin = FlowHostPrincipal.builder().userId("1").userType("ADMIN").build();
        FlowHostPrincipal portal = FlowHostPrincipal.builder().userId("1").userType("END_USER").build();
        assertTrue(OssUploaderIdentity.isSelf(object, admin));
        assertFalse(OssUploaderIdentity.isSelf(object, portal));
    }
}
