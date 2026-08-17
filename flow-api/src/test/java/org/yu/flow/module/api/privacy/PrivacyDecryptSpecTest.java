package org.yu.flow.module.api.privacy;

import org.junit.jupiter.api.Test;
import org.yu.flow.module.host.HostPrivacyProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyDecryptSpecTest {

    @Test
    void fromAlg_legacyCompoundTokens() {
        PrivacyDecryptSpec sm4 = PrivacyDecryptSpec.fromAlg("SM4_CBC");
        assertEquals("SM4", sm4.getFamily());
        assertEquals("CBC", sm4.getMode());
        assertEquals("HEX", sm4.getEncoding());
        assertEquals("PREPEND", sm4.getIvMode());

        PrivacyDecryptSpec aesEcb = PrivacyDecryptSpec.fromAlg("AES_ECB");
        assertEquals("AES", aesEcb.getFamily());
        assertEquals("ECB", aesEcb.getMode());
        assertEquals("NONE", aesEcb.getIvMode());

        PrivacyDecryptSpec gcm = PrivacyDecryptSpec.fromAlg("AES_GCM");
        assertEquals("AES", gcm.getFamily());
        assertEquals("GCM", gcm.getMode());
        assertEquals("PREPEND", gcm.getIvMode());
    }

    @Test
    void fromProfile_fillsDefaultsFromLegacyAlg() {
        HostPrivacyProfile p = new HostPrivacyProfile();
        p.setDecryptAlg("SM4_CBC");
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.fromProfile(p);
        assertEquals("SM4", spec.getFamily());
        assertEquals("CBC", spec.getMode());
        assertEquals("HEX", spec.getEncoding());
        assertEquals("PREPEND", spec.getIvMode());
    }

    @Test
    void fromProfile_explicitFieldsOverrideLegacy() {
        HostPrivacyProfile p = new HostPrivacyProfile();
        p.setDecryptAlg("AES_CBC");
        p.setDecryptMode("ECB");
        p.setDecryptEncoding("BASE64");
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.fromProfile(p);
        assertEquals("AES", spec.getFamily());
        assertEquals("ECB", spec.getMode());
        assertEquals("BASE64", spec.getEncoding());
        assertEquals("NONE", spec.getIvMode());
    }

    @Test
    void sm4Gcm_rejected() {
        HostPrivacyProfile p = new HostPrivacyProfile();
        p.setDecryptAlg("SM4");
        p.setDecryptMode("GCM");
        assertTrue(PrivacyDecryptSpec.fromProfile(p).validateMessage().contains("GCM"));
    }

    @Test
    void applyTo_writesFamilyNotCompound() {
        HostPrivacyProfile p = new HostPrivacyProfile();
        p.setDecryptAlg("SM4_CBC");
        PrivacyDecryptSpec.fromProfile(p).applyTo(p);
        assertEquals("SM4", p.getDecryptAlg());
        assertEquals("CBC", p.getDecryptMode());
        assertNull(p.getDecryptIvFixed());
    }
}
