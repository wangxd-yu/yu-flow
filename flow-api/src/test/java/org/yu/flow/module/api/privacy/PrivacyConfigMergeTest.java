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
}
