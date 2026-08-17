package org.yu.flow.login.sm2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginSm2CryptoServiceTest {

    private LoginSm2CryptoService service;

    @BeforeEach
    void setUp() {
        YuFlowProperties properties = new YuFlowProperties();
        properties.getSecurity().setSm2PasswordTtlSeconds(300L);
        service = new LoginSm2CryptoService(properties);
        service.init();
    }

    @Test
    void roundTrip_encryptDecrypt() {
        String password = "p@ss-测试-123";
        String payload = System.currentTimeMillis() + ":" + password;
        String cipher = service.encryptForTest(payload);
        assertTrue(cipher.length() > 100);
        assertEquals(password, service.decryptLoginPassword(cipher));
    }

    @Test
    void reject_expiredPayload() {
        String payload = (System.currentTimeMillis() - 400_000L) + ":old-password";
        String cipher = service.encryptForTest(payload);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.decryptLoginPassword(cipher));
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    void publicKey_isUncompressedHex() {
        String pk = service.getPublicKeyHex();
        assertTrue(pk.startsWith("04"));
        assertEquals(130, pk.length());
        assertEquals(1, service.getCipherMode());
    }
}
