package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.privacy.PrivacyDecryptAlg;
import org.yu.flow.module.host.dto.HostPrivacyProfileViewDTO;
import org.yu.flow.module.host.dto.HostPrivacyProfilesDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPrivacyProfilesStoreTest {

    @Test
    void mergeKeys_keepsPreviousWhenBlankOrStars() {
        HostPrivacyProfile old = new HostPrivacyProfile();
        old.setId("p1");
        old.setDecryptKey("0123456789abcdef");
        HostPrivacyProfiles existing = new HostPrivacyProfiles();
        existing.setProfiles(List.of(old));

        HostPrivacyProfile incoming = new HostPrivacyProfile();
        incoming.setId("p1");
        incoming.setName("员工");
        incoming.setDecryptAlg(PrivacyDecryptAlg.SM4_CBC);
        incoming.setDecryptKey("********");
        HostPrivacyProfiles next = new HostPrivacyProfiles();
        next.setProfiles(List.of(incoming));

        HostPrivacyProfiles merged = HostPrivacyProfilesStore.mergeKeys(next, existing);
        assertEquals("0123456789abcdef", merged.getProfiles().get(0).getDecryptKey());
    }

    @Test
    void mergeKeys_replacesWhenNewKeyProvided() {
        HostPrivacyProfile old = new HostPrivacyProfile();
        old.setId("p1");
        old.setDecryptKey("0123456789abcdef");
        HostPrivacyProfiles existing = new HostPrivacyProfiles();
        existing.setProfiles(List.of(old));

        HostPrivacyProfile incoming = new HostPrivacyProfile();
        incoming.setId("p1");
        incoming.setDecryptKey("abcdefghijklmnop");
        HostPrivacyProfiles next = new HostPrivacyProfiles();
        next.setProfiles(List.of(incoming));

        HostPrivacyProfiles merged = HostPrivacyProfilesStore.mergeKeys(next, existing);
        assertEquals("abcdefghijklmnop", merged.getProfiles().get(0).getDecryptKey());
    }

    @Test
    void validate_rejectsBadKeyAndRedactsView() {
        HostPrivacyProfile p = new HostPrivacyProfile();
        p.setId("p1");
        p.setName("员工");
        p.setDecryptAlg(PrivacyDecryptAlg.SM4_CBC);
        p.setDecryptKey("short");
        HostPrivacyProfiles profiles = new HostPrivacyProfiles();
        profiles.setProfiles(List.of(p));
        assertThrows(FlowException.class, () -> HostPrivacyProfilesStore.normalizeAndValidate(profiles));

        p.setDecryptKey("0123456789abcdef");
        HostPrivacyProfilesStore.normalizeAndValidate(profiles);
        assertEquals("SM4", p.getDecryptAlg());
        assertEquals("CBC", p.getDecryptMode());
        HostPrivacyProfilesStore store = new HostPrivacyProfilesStore();
        HostPrivacyProfilesDTO view = store.toView(profiles);
        HostPrivacyProfileViewDTO row = view.getProfiles().get(0);
        assertTrue(row.isDecryptKeySet());
        assertEquals("SM4", row.getDecryptAlg());
        assertEquals("CBC", row.getDecryptMode());
        assertEquals("HEX", row.getDecryptEncoding());
        assertEquals("PREPEND", row.getDecryptIvMode());
        assertFalse(String.valueOf(row).contains("0123456789abcdef"));
    }

    @Test
    void placeholderKey_blankAndStars() {
        assertTrue(HostPrivacyProfilesStore.isPlaceholderKey(null));
        assertTrue(HostPrivacyProfilesStore.isPlaceholderKey("  "));
        assertTrue(HostPrivacyProfilesStore.isPlaceholderKey("********"));
        assertFalse(HostPrivacyProfilesStore.isPlaceholderKey("0123456789abcdef"));
    }

    @Test
    void parse_blankOrInvalidIsEmptyList() {
        assertTrue(HostPrivacyProfilesStore.parse(null).getProfiles().isEmpty());
        assertTrue(HostPrivacyProfilesStore.parse("{").getProfiles().isEmpty());
    }

    @Test
    void validate_rejectsSm4Gcm() {
        HostPrivacyProfile p = new HostPrivacyProfile();
        p.setId("p1");
        p.setName("国密");
        p.setDecryptAlg("SM4");
        p.setDecryptMode("GCM");
        p.setDecryptKey("0123456789abcdef");
        HostPrivacyProfiles profiles = new HostPrivacyProfiles();
        profiles.setProfiles(List.of(p));
        assertThrows(FlowException.class, () -> HostPrivacyProfilesStore.normalizeAndValidate(profiles));
    }
}
