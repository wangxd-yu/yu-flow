package org.yu.flow.module.api.privacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyConfigMergeTest {

    @Test
    void systemDefaults_optInDisabled() {
        EffectivePrivacy eff = PrivacyConfigMerge.toEffective(
                PrivacyConfigMerge.empty(), PrivacyConfigMerge.systemDefaults());
        assertFalse(eff.isEnabled());
        assertEquals("_encrypt", eff.getFieldSuffix());
        assertTrue(eff.isStripSuffix());
        assertTrue(eff.isPrivacyField("phone_encrypt"));
        assertEquals("phone", eff.outputKey("phone_encrypt"));
        assertFalse(eff.isPrivacyField("phone"));
    }

    @Test
    void overlay_childWinsThenStopOnInheritFalse() {
        ApiPrivacyConfig merged = PrivacyConfigMerge.empty();
        ApiPrivacyConfig api = new ApiPrivacyConfig();
        api.setEnabled(true);
        api.setFieldSuffix("_sec");
        api.setInherit(false);
        assertTrue(PrivacyConfigMerge.overlay(merged, api));

        ApiPrivacyConfig dir = new ApiPrivacyConfig();
        dir.setFieldSuffix("_encrypt");
        dir.setEnabled(false);
        assertFalse(PrivacyConfigMerge.overlay(merged, dir));

        EffectivePrivacy eff = PrivacyConfigMerge.toEffective(merged, PrivacyConfigMerge.systemDefaults());
        assertTrue(eff.isEnabled());
        assertEquals("_sec", eff.getFieldSuffix());
        assertTrue(eff.isPrivacyField("mobile_sec"));
        assertFalse(eff.isPrivacyField("mobile_encrypt"));
    }

    @Test
    void extraFields_caseInsensitive() {
        ApiPrivacyConfig cfg = new ApiPrivacyConfig();
        cfg.setEnabled(true);
        cfg.getExtraFields().add("ID_NO");
        EffectivePrivacy eff = PrivacyConfigMerge.toEffective(cfg, PrivacyConfigMerge.systemDefaults());
        assertTrue(eff.isPrivacyField("id_no"));
        assertTrue(eff.isPrivacyField("ID_NO"));
        assertEquals("id_no", eff.outputKey("id_no"));
    }

    @Test
    void overlay_rulesChildReplacesParent() {
        ApiPrivacyConfig merged = PrivacyConfigMerge.empty();
        ApiPrivacyConfig api = new ApiPrivacyConfig();
        org.yu.flow.module.host.PrivacyAccessRule child = new org.yu.flow.module.host.PrivacyAccessRule();
        child.setName("child");
        child.setPrincipals("ANY_AUTHENTICATED");
        api.setRules(java.util.List.of(child));
        PrivacyConfigMerge.overlay(merged, api);

        ApiPrivacyConfig dir = new ApiPrivacyConfig();
        org.yu.flow.module.host.PrivacyAccessRule parent = new org.yu.flow.module.host.PrivacyAccessRule();
        parent.setName("parent");
        dir.setRules(java.util.List.of(parent));
        PrivacyConfigMerge.overlay(merged, dir);

        assertEquals(1, merged.getRules().size());
        assertEquals("child", merged.getRules().get(0).getName());
    }

    @Test
    void overlay_nullRulesInheritParent() {
        ApiPrivacyConfig merged = PrivacyConfigMerge.empty();
        ApiPrivacyConfig api = new ApiPrivacyConfig();
        api.setEnabled(true);
        PrivacyConfigMerge.overlay(merged, api);

        ApiPrivacyConfig dir = new ApiPrivacyConfig();
        org.yu.flow.module.host.PrivacyAccessRule parent = new org.yu.flow.module.host.PrivacyAccessRule();
        parent.setName("parent");
        dir.setRules(java.util.List.of(parent));
        PrivacyConfigMerge.overlay(merged, dir);

        assertEquals("parent", merged.getRules().get(0).getName());
    }

    @Test
    void overlay_profileIdChildWins() {
        ApiPrivacyConfig merged = PrivacyConfigMerge.empty();
        ApiPrivacyConfig api = new ApiPrivacyConfig();
        api.setProfileId("staff-sm4");
        PrivacyConfigMerge.overlay(merged, api);

        ApiPrivacyConfig dir = new ApiPrivacyConfig();
        dir.setProfileId("guest-aes");
        PrivacyConfigMerge.overlay(merged, dir);

        assertEquals("staff-sm4", merged.getProfileId());
    }

    @Test
    void mergeMask_unionsAliasesPerType() {
        java.util.Map<String, java.util.List<String>> overlay = new java.util.LinkedHashMap<>();
        overlay.put(PrivacyMasker.PHONE, java.util.List.of("loginPhone", "authPhone"));
        java.util.Map<String, java.util.List<String>> merged =
                PrivacyConfigMerge.mergeMask(PrivacyConfigMerge.defaultMask(), overlay);
        assertTrue(merged.get(PrivacyMasker.PHONE).contains("phone"));
        assertTrue(merged.get(PrivacyMasker.PHONE).contains("loginPhone"));
        assertTrue(merged.get(PrivacyMasker.PHONE).contains("authPhone"));
        assertTrue(merged.containsKey(PrivacyMasker.NAME));
    }

    @Test
    void overlay_maskRulesOverrideProfile() {
        ApiPrivacyConfig merged = PrivacyConfigMerge.empty();
        ApiPrivacyConfig dir = new ApiPrivacyConfig();
        dir.setEnabled(true);
        PrivacyMaskRule child = new PrivacyMaskRule();
        child.setAliases(java.util.List.of("loginPhone", "authPhone"));
        child.setMatchMode(PrivacyMaskRule.MATCH_CONTAINS);
        child.setMethod(PrivacyMaskRule.PHONE);
        dir.setMaskRules(java.util.List.of(child));
        PrivacyConfigMerge.overlay(merged, dir);

        org.yu.flow.module.host.HostPrivacyProfile profile = new org.yu.flow.module.host.HostPrivacyProfile();
        profile.setId("p1");
        PrivacyMaskRule profileRule = new PrivacyMaskRule();
        profileRule.setAliases(java.util.List.of("mobile"));
        profileRule.setMethod(PrivacyMaskRule.FULL);
        profile.setRules(java.util.List.of(profileRule));

        EffectivePrivacy eff = PrivacyConfigMerge.toEffective(merged, PrivacyConfigMerge.systemDefaults(), profile);
        assertEquals(1, eff.getMaskRules().size());
        assertEquals(PrivacyMaskRule.PHONE, eff.getMaskRules().get(0).getMethod());
        assertTrue(eff.getMaskRules().get(0).getAliases().contains("loginPhone"));
    }

    @Test
    void toEffective_usesProfileDecryptAndRules() {
        ApiPrivacyConfig cfg = new ApiPrivacyConfig();
        cfg.setEnabled(true);
        org.yu.flow.module.host.HostPrivacyProfile profile = new org.yu.flow.module.host.HostPrivacyProfile();
        profile.setId("p1");
        profile.setDecryptAlg("AES_CBC");
        profile.setDecryptKey("abcdefghijklmnop");
        PrivacyMaskRule rule = new PrivacyMaskRule();
        rule.setAliases(java.util.List.of("mobile"));
        rule.setMethod(PrivacyMaskRule.KEEP_HEAD_TAIL);
        rule.setKeepHead(3);
        rule.setKeepTail(4);
        rule.setMaskLen(4);
        profile.setRules(java.util.List.of(rule));

        EffectivePrivacy eff = PrivacyConfigMerge.toEffective(cfg, PrivacyConfigMerge.systemDefaults(), profile);
        assertEquals("p1", eff.getProfileId());
        assertEquals("AES", eff.getDecryptAlg());
        assertEquals("CBC", eff.getDecryptSpec().getMode());
        assertEquals("HEX", eff.getDecryptSpec().getEncoding());
        assertEquals("PREPEND", eff.getDecryptSpec().getIvMode());
        assertEquals("abcdefghijklmnop", eff.getDecryptKey());
        assertEquals(1, eff.getMaskRules().size());
        assertEquals("KEEP_HEAD_TAIL", eff.getMaskRules().get(0).getMethod());
    }

    @Test
    void toEffective_emptyRulesDoNotInjectSilentDefaults() {
        ApiPrivacyConfig cfg = new ApiPrivacyConfig();
        cfg.setEnabled(true);
        org.yu.flow.module.host.HostPrivacyProfile profile = new org.yu.flow.module.host.HostPrivacyProfile();
        profile.setId("p1");
        profile.setRules(java.util.List.of());

        EffectivePrivacy eff = PrivacyConfigMerge.toEffective(cfg, PrivacyConfigMerge.systemDefaults(), profile);
        assertTrue(eff.getMaskRules().isEmpty());
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "loginPhone", eff.getMaskRules()));
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "phone", eff.getMaskRules()));
    }
}
