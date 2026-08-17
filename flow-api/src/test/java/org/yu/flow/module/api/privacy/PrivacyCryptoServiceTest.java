package org.yu.flow.module.api.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.login.sm2.LoginSm2CryptoService;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyCryptoServiceTest {

    private PrivacyCryptoService crypto;
    private LoginSm2CryptoService sm2;

    @BeforeEach
    void setUp() throws Exception {
        YuFlowProperties props = new YuFlowProperties();
        props.getPrivacy().setAtRestSm4Key("0123456789abcdef");
        sm2 = new LoginSm2CryptoService(props);
        sm2.init();
        crypto = new PrivacyCryptoService();
        setField(crypto, "yuFlowProperties", props);
        setField(crypto, "loginSm2CryptoService", sm2);
    }

    @Test
    void atRest_roundTrip() {
        String cipher = crypto.encryptAtRest("13812341234");
        assertTrue(cipher.length() > 32);
        assertEquals("13812341234", crypto.decryptAtRest(cipher).orElseThrow());
    }

    @Test
    void decryptFail_empty() {
        assertTrue(crypto.decryptAtRest("not-hex").isEmpty());
        assertTrue(crypto.decryptAtRest("").isEmpty());
    }

    @Test
    void transportWrap_andSessionKeyFromSm2Hex() {
        byte[] session = "1234567890abcdef".getBytes(StandardCharsets.UTF_8);
        String sm2Cipher = sm2.encryptForTest(new String(session, StandardCharsets.UTF_8));
        byte[] unwrapped = crypto.unwrapTransportKey(sm2Cipher).orElseThrow();
        assertEquals(16, unwrapped.length);

        Map<String, Object> envelope = crypto.wrapTransport("张三丰", session);
        assertEquals(1, envelope.get(PrivacyCryptoService.ENC_FLAG));
        assertEquals("SM4", envelope.get(PrivacyCryptoService.ENC_ALG));
        String hex = String.valueOf(envelope.get(PrivacyCryptoService.ENC_VAL));
        assertEquals("张三丰", PrivacyCryptoService.sm4CbcDecrypt(session, hex));
    }

    @Test
    void missingKey_returnsEmpty() throws Exception {
        YuFlowProperties blank = new YuFlowProperties();
        PrivacyCryptoService noKey = new PrivacyCryptoService();
        setField(noKey, "yuFlowProperties", blank);
        assertTrue(noKey.decryptAtRest(crypto.encryptAtRest("x")).isEmpty());
    }

    @Test
    void aesCbc_profileKeyRoundTrip() {
        String key = "abcdefghijklmnop";
        String cipher = crypto.encryptAtRest("13812341234", PrivacyDecryptAlg.AES_CBC, key);
        assertEquals("13812341234",
                crypto.decryptAtRest(cipher, PrivacyDecryptAlg.AES_CBC, key).orElseThrow());
        assertTrue(crypto.decryptAtRest(cipher, PrivacyDecryptAlg.SM4_CBC, "0123456789abcdef").isEmpty());
    }

    @Test
    void plainAlg_returnsAsIs() {
        assertEquals("明文", crypto.decryptAtRest("明文", PrivacyDecryptAlg.PLAIN, null).orElseThrow());
    }

    @Test
    void aesDoesNotFallbackToYamlSm4() {
        String cipher = crypto.encryptAtRest("x", PrivacyDecryptAlg.AES_CBC, "abcdefghijklmnop");
        assertTrue(crypto.decryptAtRest(cipher, PrivacyDecryptAlg.AES_CBC, null).isEmpty());
    }

    @Test
    void aesEcb_base64RoundTrip() {
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.of(
                PrivacyDecryptSpec.FAMILY_AES, PrivacyDecryptSpec.MODE_ECB,
                PrivacyDecryptSpec.ENC_BASE64, PrivacyDecryptSpec.IV_NONE, null);
        String key = "abcdefghijklmnop";
        String cipher = crypto.encryptAtRest("13812341234", spec, key);
        assertEquals("13812341234", crypto.decryptAtRest(cipher, spec, key).orElseThrow());
    }

    @Test
    void sm4Cbc_zeroIvRoundTrip() {
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.of(
                PrivacyDecryptSpec.FAMILY_SM4, PrivacyDecryptSpec.MODE_CBC,
                PrivacyDecryptSpec.ENC_HEX, PrivacyDecryptSpec.IV_NONE, null);
        String cipher = crypto.encryptAtRest("hello", spec, "0123456789abcdef");
        assertEquals("hello", crypto.decryptAtRest(cipher, spec, "0123456789abcdef").orElseThrow());
    }

    @Test
    void aesCbc_fixedIvRoundTrip() {
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.of(
                PrivacyDecryptSpec.FAMILY_AES, PrivacyDecryptSpec.MODE_CBC,
                PrivacyDecryptSpec.ENC_HEX, PrivacyDecryptSpec.IV_FIXED, "1234567890abcdef");
        String key = "abcdefghijklmnop";
        String cipher = crypto.encryptAtRest("fixed-iv", spec, key);
        assertEquals("fixed-iv", crypto.decryptAtRest(cipher, spec, key).orElseThrow());
    }

    @Test
    void aesGcm_prependRoundTrip() {
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.fromAlg("AES_GCM");
        String key = "abcdefghijklmnop";
        String cipher = crypto.encryptAtRest("gcm-plain", spec, key);
        assertEquals("gcm-plain", crypto.decryptAtRest(cipher, spec, key).orElseThrow());
        assertTrue(crypto.decryptAtRest(cipher, PrivacyDecryptAlg.AES_CBC, key).isEmpty());
    }

    @Test
    void legacySm4CbcHex_stillDecryptsWithFamilySpec() {
        String cipher = crypto.encryptAtRest("13812341234", PrivacyDecryptAlg.SM4_CBC, "0123456789abcdef");
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.fromAlg("SM4");
        assertEquals("13812341234", crypto.decryptAtRest(cipher, spec, "0123456789abcdef").orElseThrow());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
