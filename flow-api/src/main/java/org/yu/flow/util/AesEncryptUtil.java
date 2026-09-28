package org.yu.flow.util;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yu.flow.config.YuFlowProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 对称加密工具（用于数据源密码等敏感信息的加解密）。
 *
 * <p>密钥通过 {@link YuFlowProperties.Security#getAesSecretKey()} 获取，
 * 对应 YAML 配置路径：{@code yu.flow.security.aes-secret-key}。</p>
 *
 * <p>新密文为 AES-GCM（随机 IV + 校验标签，格式 {@code g1:Base64(IV‖密文‖标签)}）：同一明文每次密文不同，
 * 被篡改会解密失败。旧版 AES/ECB 十六进制密文仍可解密，下次保存时自动换成新格式。</p>
 *
 * @author yu-flow
 * @since 1.0
 */
@Component
public class AesEncryptUtil {
    private static final Logger logger = LoggerFactory.getLogger(AesEncryptUtil.class);

    static final String GCM_PREFIX = "g1:";
    private static final String DEFAULT_KEY = "flow-secure-keys";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final YuFlowProperties properties;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec keySpec;
    /** 仅用于解密旧版 ECB 密文 */
    private AES legacy;

    /**
     * 构造器注入统一配置属性。
     *
     * @param properties yu-flow 配置树
     */
    public AesEncryptUtil(YuFlowProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        String secretKey = properties.getSecurity().getAesSecretKey();
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException(
                    "AES密钥未配置，请在 application.yml 中设置 yu.flow.security.aes-secret-key");
        }
        byte[] key = secretKey.getBytes(StandardCharsets.UTF_8);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException(
                    "AES密钥长度非法，需为16/24/32字节（当前长度：" + key.length + "）");
        }
        if (DEFAULT_KEY.equals(secretKey)) {
            logger.warn("yu.flow.security.aes-secret-key 仍是公开的默认值，密文形同明文；请为每个环境配置独立密钥"
                    + "（YU_FLOW_AES_SECRET），更换前需按新密钥重新保存已加密的口令");
        }
        keySpec = new SecretKeySpec(key, "AES");
        legacy = SecureUtil.aes(key);
    }

    public String encrypt(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("原始密码不能为null");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return GCM_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            logger.error("解密失败：加密密码为空");
            throw new IllegalArgumentException("加密密码不能为空");
        }
        if (encryptedPassword.startsWith(GCM_PREFIX)) {
            return decryptGcm(encryptedPassword.substring(GCM_PREFIX.length()));
        }
        if (encryptedPassword.length() % 2 != 0) {
            logger.error("解密失败：密文格式非法（长度 {}）", encryptedPassword.length());
            throw new IllegalArgumentException("加密密码格式非法");
        }
        try {
            return legacy.decryptStr(encryptedPassword);
        } catch (Exception e) {
            logger.error("解密失败：旧版密文无法解密，请确认 aes-secret-key 与加密时一致");
            throw new RuntimeException("密码解密失败", e);
        }
    }

    private String decryptGcm(String payload) {
        try {
            byte[] data = Base64.getDecoder().decode(payload);
            if (data.length < IV_BYTES + TAG_BITS / 8) {
                throw new IllegalArgumentException("密文过短");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("解密失败：密文被篡改或 aes-secret-key 与加密时不一致");
            throw new RuntimeException("密码解密失败", e);
        }
    }
}
