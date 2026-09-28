package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.login.sm2.LoginSm2CryptoService;
import org.yu.flow.module.api.dto.PrivacyDevSessionDTO;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyDevSessionServiceTest {

    private YuFlowProperties props;
    private StandardEnvironment env;
    private LoginSm2CryptoService sm2;
    private PrivacyCryptoService crypto;
    private PrivacyDevSessionService service;

    @BeforeEach
    void setUp() throws Exception {
        props = new YuFlowProperties();
        env = new StandardEnvironment();
        sm2 = new LoginSm2CryptoService(props);
        sm2.init();
        crypto = new PrivacyCryptoService();
        setField(crypto, "yuFlowProperties", props);
        setField(crypto, "loginSm2CryptoService", sm2);
        service = new PrivacyDevSessionService(props, env, sm2);
    }

    @Test
    void createSession_headerUnwrapsToSm4KeyHex() {
        props.getPrivacy().setDevSessionEnabled(true);
        PrivacyDevSessionDTO dto = service.createSession();
        assertEquals(PrivacyCryptoService.HEADER_PRIVACY_KEY, dto.getHeaderName());
        assertEquals(1, dto.getCipherMode());
        assertEquals(32, dto.getSm4KeyHex().length());
        byte[] unwrapped = crypto.unwrapTransportKey(dto.getHeaderValue()).orElseThrow();
        assertEquals(dto.getSm4KeyHex(), HexUtil.encodeHexStr(unwrapped));
    }

    @Test
    void unavailable_whenDefaultAndNoLocalProfile() {
        assertFalse(service.isAvailable());
        assertThrows(IllegalStateException.class, service::createSession);
    }

    @Test
    void available_whenDevProfileEvenIfFlagFalse() {
        env.setActiveProfiles("dev");
        service = new PrivacyDevSessionService(props, env, sm2);
        assertTrue(service.isAvailable());
    }

    @Test
    void denied_whenProdEvenIfFlagTrue() {
        props.getPrivacy().setDevSessionEnabled(true);
        env.setActiveProfiles("prod");
        service = new PrivacyDevSessionService(props, env, sm2);
        assertFalse(service.isAvailable());
    }

    @Test
    void denied_whenDemoMode() {
        props.setDemoMode(true);
        props.getPrivacy().setDevSessionEnabled(true);
        env.setActiveProfiles("dev");
        service = new PrivacyDevSessionService(props, env, sm2);
        assertFalse(service.isAvailable());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
