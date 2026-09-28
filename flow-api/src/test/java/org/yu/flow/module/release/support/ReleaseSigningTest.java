package org.yu.flow.module.release.support;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseSigningTest {

    private static ReleaseSigning signing(String key) {
        return signing(key, null, new YuFlowProperties());
    }

    private static ReleaseSigning signing(String key, String currentEnv, YuFlowProperties props) {
        props.getRelease().setSigningKey(key);
        props.getRelease().setCurrentEnv(currentEnv);
        ReleaseEnvironment env = new ReleaseEnvironment();
        ReflectionTestUtils.setField(env, "yuFlowProperties", props);
        ReleaseSigning signing = new ReleaseSigning();
        ReflectionTestUtils.setField(signing, "yuFlowProperties", props);
        ReflectionTestUtils.setField(signing, "releaseEnvironment", env);
        return signing;
    }

    @Test
    void statusMatrix() {
        byte[] manifest = "{\"releaseCode\":\"v1\"}".getBytes(StandardCharsets.UTF_8);
        String sig = ReleaseSigning.sign(manifest, "k1");

        assertEquals(ReleaseSigning.VERIFIED, signing("k1").status(manifest, sig));
        assertEquals(ReleaseSigning.INVALID, signing("k2").status(manifest, sig));
        assertEquals(ReleaseSigning.INVALID, signing("k1").status("tampered".getBytes(StandardCharsets.UTF_8), sig));
        assertEquals(ReleaseSigning.UNSIGNED, signing("k1").status(manifest, null));
        assertEquals(ReleaseSigning.UNVERIFIABLE, signing(null).status(manifest, sig));
        assertEquals(ReleaseSigning.DISABLED, signing(" ").status(manifest, null));
    }

    @Test
    void previousKeysStillVerifyDuringRotation() {
        byte[] manifest = "{\"releaseCode\":\"v1\"}".getBytes(StandardCharsets.UTF_8);
        String oldSig = ReleaseSigning.sign(manifest, "old-key");
        YuFlowProperties props = new YuFlowProperties();
        props.getRelease().setPreviousSigningKeys(List.of("old-key", " "));

        ReleaseSigning rotated = signing("new-key", null, props);

        assertEquals(ReleaseSigning.VERIFIED, rotated.status(manifest, oldSig));
        assertEquals("new-key", rotated.key());
        assertEquals(ReleaseSigning.VERIFIED, rotated.status(manifest, ReleaseSigning.sign(manifest, "new-key")));
    }

    @Test
    void requiredForProdOrLockedUnlessConfigured() {
        assertFalse(signing(null).required());
        assertTrue(signing(null, "prod", new YuFlowProperties()).required());

        YuFlowProperties locked = new YuFlowProperties();
        locked.getRelease().setLockAssetEditing(true);
        assertTrue(signing(null, "UAT", locked).required());

        YuFlowProperties optedOut = new YuFlowProperties();
        optedOut.getRelease().setRequireSignature(false);
        assertFalse(signing(null, "PROD", optedOut).required());

        YuFlowProperties optedIn = new YuFlowProperties();
        optedIn.getRelease().setRequireSignature(true);
        assertTrue(signing(null, "DEV", optedIn).required());
    }

    @Test
    void signedPackageRoundTrip() throws Exception {
        ReleasePackageFormat.ReleaseInfo info = new ReleasePackageFormat.ReleaseInfo();
        info.setCode("v2026.10");
        AssetBundle bundle = new AssetBundle();
        bundle.setKind(AssetBundle.KIND);
        bundle.setSchemaVersion(AssetBundle.SCHEMA_VERSION);

        ReleasePackageReader.Parsed parsed = ReleasePackageReader.read(ReleasePackageWriter.write(info, bundle, "k1").bytes());

        assertNotNull(parsed.signature());
        assertEquals(ReleaseSigning.VERIFIED, signing("k1").status(parsed.manifestBytes(), parsed.signature()));
    }
}
