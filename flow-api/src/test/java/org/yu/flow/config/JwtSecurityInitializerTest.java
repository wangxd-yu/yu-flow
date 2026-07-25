package org.yu.flow.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.auto.util.JwtTokenUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT/AES/管理员密码默认弱密钥启动拦截测试。
 */
class JwtSecurityInitializerTest {

    private static final String SAFE_JWT_SECRET = "this-is-a-very-strong-jwt-secret-key-at-least-32-chars";
    private static final String SAFE_AES_SECRET = "0123456789abcdef"; // 16 字节合法 AES 密钥

    private JwtSecurityInitializer initializer;
    private YuFlowProperties properties;

    @BeforeEach
    void setUp() {
        initializer = new JwtSecurityInitializer();
        properties = new YuFlowProperties();
        properties.setSecurity(new YuFlowProperties.Security());
        // 重置为默认弱密钥，模拟未配置环境变量的场景
        JwtTokenUtil.init(JwtTokenUtil.LEGACY_DEFAULT_SECRET, 7200L);
    }

    @AfterEach
    void tearDown() {
        // 每个用例结束后重置静态状态，避免影响其他测试
        JwtTokenUtil.init(JwtTokenUtil.LEGACY_DEFAULT_SECRET, 7200L);
    }

    private void inject(String jwtSecret, String aesSecret, String adminPassword) {
        properties.getSecurity().setJwtSecretKey(jwtSecret);
        properties.getSecurity().setAesSecretKey(aesSecret);
        properties.setPassword(adminPassword);
        org.springframework.test.util.ReflectionTestUtils.setField(initializer, "yuFlowProperties", properties);
    }

    @Test
    void defaultJwtSecret_blocksStartup_whenFailClosed() {
        inject(JwtTokenUtil.LEGACY_DEFAULT_SECRET, SAFE_AES_SECRET, "not-123456");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> initializer.run(null));
        assertTrue(ex.getMessage().contains("JWT"));
        assertTrue(ex.getMessage().contains("历史默认密钥"));
    }

    @Test
    void shortJwtSecret_blocksStartup_whenFailClosed() {
        inject("short", SAFE_AES_SECRET, "not-123456");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> initializer.run(null));
        assertTrue(ex.getMessage().contains("JWT"));
        assertTrue(ex.getMessage().contains("密钥过弱"));
    }

    @Test
    void safeJwtSecret_allowsStartup() {
        inject(SAFE_JWT_SECRET, SAFE_AES_SECRET, "not-123456");
        assertDoesNotThrow(() -> initializer.run(null));
        assertFalse(JwtTokenUtil.isUsingLegacyDefaultSecret());
    }

    @Test
    void defaultAesSecret_blocksStartup_whenFailClosed() {
        inject(SAFE_JWT_SECRET, JwtSecurityInitializer.LEGACY_AES_DEFAULT, "not-123456");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> initializer.run(null));
        assertTrue(ex.getMessage().contains("AES"));
        assertTrue(ex.getMessage().contains("默认值"));
    }

    @Test
    void invalidLengthAesSecret_blocksStartup_whenFailClosed() {
        inject(SAFE_JWT_SECRET, "short-aes-key", "not-123456");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> initializer.run(null));
        assertTrue(ex.getMessage().contains("AES"));
        assertTrue(ex.getMessage().contains("长度不合法"));
    }

    @Test
    void safeAesSecret_allowsStartup() {
        inject(SAFE_JWT_SECRET, SAFE_AES_SECRET, "not-123456");
        assertDoesNotThrow(() -> initializer.run(null));
    }

    @Test
    void defaultAdminPassword_blocksStartup_whenFailClosed() {
        inject(SAFE_JWT_SECRET, SAFE_AES_SECRET, "123456");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> initializer.run(null));
        assertTrue(ex.getMessage().contains("ADMIN"));
        assertTrue(ex.getMessage().contains("123456"));
    }

    @Test
    void insecureDefaults_allowed_whenFailOpen() {
        properties.getSecurity().setFailOnInsecureDefaults(false);
        inject(JwtTokenUtil.LEGACY_DEFAULT_SECRET, JwtSecurityInitializer.LEGACY_AES_DEFAULT, "123456");
        assertDoesNotThrow(() -> initializer.run(null));
    }
}
