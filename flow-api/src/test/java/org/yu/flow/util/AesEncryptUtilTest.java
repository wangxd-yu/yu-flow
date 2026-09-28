package org.yu.flow.util;

import cn.hutool.crypto.SecureUtil;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesEncryptUtilTest {

    private static final String KEY = "0123456789abcdef";

    private static AesEncryptUtil util() {
        YuFlowProperties props = new YuFlowProperties();
        props.getSecurity().setAesSecretKey(KEY);
        AesEncryptUtil util = new AesEncryptUtil(props);
        util.init();
        return util;
    }

    @Test
    void gcmRoundTripWithRandomIv() {
        AesEncryptUtil util = util();
        String a = util.encrypt("数据库口令-123");
        String b = util.encrypt("数据库口令-123");

        assertTrue(a.startsWith(AesEncryptUtil.GCM_PREFIX));
        assertNotEquals(a, b);
        assertEquals("数据库口令-123", util.decrypt(a));
        assertEquals("", util.decrypt(util.encrypt("")));
    }

    @Test
    void legacyEcbCiphertextStillDecrypts() {
        String legacy = SecureUtil.aes(KEY.getBytes(StandardCharsets.UTF_8)).encryptHex("old-password");
        assertEquals("old-password", util().decrypt(legacy));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        AesEncryptUtil util = util();
        byte[] raw = Base64.getDecoder().decode(util.encrypt("secret").substring(AesEncryptUtil.GCM_PREFIX.length()));
        raw[raw.length - 1] ^= 1;
        String tampered = AesEncryptUtil.GCM_PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThrows(RuntimeException.class, () -> util.decrypt(tampered));
    }

    @Test
    void ciphertextFitsDatasourcePasswordColumn() {
        String longPassword = "p".repeat(200);
        assertTrue(util().encrypt(longPassword).length() <= 512);
    }
}
