package org.yu.flow.module.oss.support;

import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.HostPrincipalSettings;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssAccessSupportTest {

    @Test
    void anyAuthenticated_self_seesOwnOnly() {
        OssAccessRules rules = OssAccessSupport.parseJson("""
                {"rules":[{"name":"登录","principals":"ANY_AUTHENTICATED","upload":true,"downloadScope":"SELF"}]}
                """);
        OssObjectDO mine = object("u-end", "d1");
        OssObjectDO others = object("u-other", "d1");
        OssAccessSupport.OssDownloadGrant grant = OssAccessSupport.mergeDownload(rules, endUser());
        assertTrue(OssAccessSupport.allowsObject(mine, grant, endUser(), Set.of("d1")));
        assertFalse(OssAccessSupport.allowsObject(others, grant, endUser(), Set.of("d1")));
        assertTrue(OssAccessSupport.canUpload(rules, endUser()));
        assertFalse(OssAccessSupport.canUpload(rules, openApp()));
    }

    @Test
    void opsAll_andUserSelf_mostPermissive() {
        OssAccessRules rules = OssAccessSupport.parseJson("""
                {"rules":[
                  {"name":"用户","principals":"ANY_AUTHENTICATED","upload":true,"downloadScope":"SELF"},
                  {"name":"运营","principals":"MATCH","userTypes":["STAFF"],"upload":false,"downloadScope":"ALL"}
                ]}
                """);
        assertTrue(OssAccessSupport.mergeDownload(rules, staff()).all);
        assertTrue(OssAccessSupport.mergeDownload(rules, endUser()).self);
        assertFalse(OssAccessSupport.mergeDownload(rules, endUser()).all);
        assertTrue(OssAccessSupport.allowsObject(object("u-other", "d9"),
                OssAccessSupport.mergeDownload(rules, staff()), staff(), Set.of("d1")));
    }

    @Test
    void dept_andSelf_union() {
        OssAccessRules rules = OssAccessSupport.parseJson("""
                {"rules":[
                  {"name":"本人","principals":"ANY_AUTHENTICATED","upload":true,"downloadScope":"SELF"},
                  {"name":"主管","principals":"MATCH","roles":["DEPT_LEAD"],"upload":true,"downloadScope":"DEPT"}
                ]}
                """);
        FlowHostPrincipal lead = FlowHostPrincipal.builder()
                .userId("lead-1")
                .userType("END_USER")
                .roles(Set.of("DEPT_LEAD"))
                .deptId("d1")
                .authChannel(HostPrincipalSettings.CHANNEL_API)
                .build();
        OssAccessSupport.OssDownloadGrant grant = OssAccessSupport.mergeDownload(rules, lead);
        assertTrue(grant.self);
        assertTrue(grant.dept);
        assertTrue(OssAccessSupport.allowsObject(object("lead-1", "x"), grant, lead, Set.of("d1", "d1-1")));
        assertTrue(OssAccessSupport.allowsObject(object("other", "d1-1"), grant, lead, Set.of("d1", "d1-1")));
        assertFalse(OssAccessSupport.allowsObject(object("other", "d9"), grant, lead, Set.of("d1", "d1-1")));
    }

    @Test
    void flowJwt_skipsHostRules() {
        OssAccessRules rules = OssAccessSupport.parseJson("""
                {"rules":[{"principals":"MATCH","userTypes":["STAFF"],"upload":true,"downloadScope":"ALL"}]}
                """);
        FlowHostPrincipal jwt = FlowHostPrincipal.builder()
                .userId("admin")
                .userType("ADMIN")
                .authChannel("FLOW_JWT")
                .build();
        OssUploadProfileDO profile = privateProfile(rules);
        assertDoesNotThrow(() -> OssAccessSupport.assertHostUpload(profile, jwt));
        assertDoesNotThrow(() -> OssAccessSupport.assertHostDownload(profile, jwt));
    }

    @Test
    void privateRequireAuth_requiresRules() {
        OssUploadProfileDO profile = new OssUploadProfileDO();
        profile.setVisibility(OssUploadProfileDO.VISIBILITY_PRIVATE);
        profile.setRequireAuth(true);
        profile.setCallerPolicy("{\"rules\":[]}");
        assertThrows(FlowException.class, () -> OssAccessSupport.validateOnSave(profile));
    }

    @Test
    void matchWithoutIdentity_rejected() {
        OssUploadProfileDO profile = privateProfile(OssAccessSupport.parseJson("""
                {"rules":[{"principals":"MATCH","upload":true,"downloadScope":"SELF"}]}
                """));
        assertThrows(FlowException.class, () -> OssAccessSupport.validateOnSave(profile));
    }

    @Test
    void migrateLegacy_uploadAndDownload() {
        OssAccessRules rules = OssAccessSupport.parseJson("""
                {"upload":{"enabled":true,"userTypes":["END_USER"]},"download":{"enabled":true,"userTypes":["STAFF"]}}
                """);
        assertTrue(rules.getRules().size() >= 2);
        assertTrue(OssAccessSupport.canUpload(rules, endUser()));
        assertTrue(OssAccessSupport.canDownload(rules, staff()));
    }

    @Test
    void openApp_principals_notCoveredByAnyLogin() {
        OssAccessRules any = OssAccessSupport.parseJson("""
                {"rules":[{"principals":"ANY_AUTHENTICATED","upload":true,"downloadScope":"SELF"}]}
                """);
        OssAccessRules open = OssAccessSupport.parseJson("""
                {"rules":[{"principals":"OPEN_APP","upload":true,"downloadScope":"ALL"}]}
                """);
        OssAccessRules specific = OssAccessSupport.parseJson("""
                {"rules":[{"principals":"OPEN_APP","userIds":["demo"],"upload":true,"downloadScope":"ALL"}]}
                """);
        assertFalse(OssAccessSupport.canUpload(any, openApp()));
        assertTrue(OssAccessSupport.canUpload(open, openApp()));
        assertFalse(OssAccessSupport.canUpload(open, endUser()));
        FlowHostPrincipal demoApp = FlowHostPrincipal.builder()
                .userId("open:demo")
                .username("demo")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .build();
        assertTrue(OssAccessSupport.canUpload(specific, demoApp));
        FlowHostPrincipal otherApp = FlowHostPrincipal.builder()
                .userId("open:other")
                .username("other")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .build();
        assertFalse(OssAccessSupport.canUpload(specific, otherApp));
    }

    @Test
    void public_stripsDownloadScope() {
        OssUploadProfileDO profile = new OssUploadProfileDO();
        profile.setVisibility(OssUploadProfileDO.VISIBILITY_PUBLIC);
        profile.setRequireAuth(true);
        profile.setCallerPolicy("""
                {"rules":[{"principals":"ANY_AUTHENTICATED","upload":true,"downloadScope":"ALL"}]}
                """);
        OssAccessSupport.validateOnSave(profile);
        OssAccessRules saved = OssAccessSupport.parse(profile);
        assertTrue(OssAccessRule.SCOPE_OFF.equals(saved.getRules().get(0).getDownloadScope()));
    }

    @Test
    void sameUserId_differentUserType_notSelf() {
        OssAccessRules rules = OssAccessSupport.parseJson("""
                {"rules":[{"name":"登录","principals":"ANY_AUTHENTICATED","upload":true,"downloadScope":"SELF"}]}
                """);
        OssObjectDO adminFile = object("1", "ADMIN", "0");
        OssAccessSupport.OssDownloadGrant grant = OssAccessSupport.mergeDownload(rules, endUserId("1"));
        assertFalse(OssAccessSupport.allowsObject(adminFile, grant, endUserId("1"), Set.of()));
        assertTrue(OssAccessSupport.allowsObject(adminFile, grant, adminId("1"), Set.of()));
    }

    private static OssUploadProfileDO privateProfile(OssAccessRules rules) {
        OssUploadProfileDO profile = new OssUploadProfileDO();
        profile.setVisibility(OssUploadProfileDO.VISIBILITY_PRIVATE);
        profile.setRequireAuth(true);
        profile.setEnabled(true);
        profile.setCallerPolicy(OssAccessSupport.toJson(rules));
        return profile;
    }

    private static OssObjectDO object(String uploadedBy, String deptId) {
        return object(uploadedBy, "END_USER", deptId);
    }

    private static OssObjectDO object(String uploadedBy, String userType, String deptId) {
        OssObjectDO object = new OssObjectDO();
        object.setStatus(OssObjectDO.STATUS_ACTIVE);
        object.setVisibility(OssObjectDO.VISIBILITY_PRIVATE);
        object.setProfileCode("docs");
        object.setUploadedBy(uploadedBy);
        object.setUploadedByUserType(userType);
        object.setDeptId(deptId);
        return object;
    }

    private static FlowHostPrincipal endUser() {
        return endUserId("u-end");
    }

    private static FlowHostPrincipal endUserId(String userId) {
        return FlowHostPrincipal.builder()
                .userId(userId)
                .userType("END_USER")
                .authChannel(HostPrincipalSettings.CHANNEL_API)
                .build();
    }

    private static FlowHostPrincipal adminId(String userId) {
        return FlowHostPrincipal.builder()
                .userId(userId)
                .userType("ADMIN")
                .authChannel(HostPrincipalSettings.CHANNEL_API)
                .build();
    }

    private static FlowHostPrincipal staff() {
        return FlowHostPrincipal.builder()
                .userId("staff-1")
                .userType("STAFF")
                .authChannel(HostPrincipalSettings.CHANNEL_API)
                .build();
    }

    private static FlowHostPrincipal openApp() {
        return FlowHostPrincipal.builder()
                .userId("open:app")
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .build();
    }
}
