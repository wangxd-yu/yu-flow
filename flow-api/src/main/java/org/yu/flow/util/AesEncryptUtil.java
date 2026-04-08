package org.yu.flow.util;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yu.flow.config.YuFlowProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * AES 对称加密工具（用于数据源密码等敏感信息的加解密）。
 *
 * <p>密钥通过 {@link YuFlowProperties.Security#getAesSecretKey()} 获取，
 * 对应 YAML 配置路径：{@code yu.flow.security.aes-secret-key}。</p>
 *
 * @author yu-flow
 * @since 1.0
 */
@Component
public class AesEncryptUtil {
    private static final Logger logger = LoggerFactory.getLogger(AesEncryptUtil.class);

    private final YuFlowProperties properties;
    private AES aesInstance;

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
        if (secretKey.getBytes().length % 16 != 0) {
            throw new IllegalStateException(
                    "AES密钥长度非法，需为16/24/32字节（当前长度：" + secretKey.getBytes().length + "）");
        }
        aesInstance = SecureUtil.aes(secretKey.getBytes());
    }

    public String encrypt(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("原始密码不能为null");
        }
        return aesInstance.encryptHex(plainPassword);
    }

    public String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            logger.error("解密失败：加密密码为空");
            throw new IllegalArgumentException("加密密码不能为空");
        }
        if (encryptedPassword.length() % 2 != 0) {
            logger.error("解密失败：加密密码长度非法（需为偶数），实际长度：{}，值：{}",
                    encryptedPassword.length(), encryptedPassword);
            throw new IllegalArgumentException("加密密码格式非法");
        }
        try {
            return aesInstance.decryptStr(encryptedPassword);
        } catch (Exception e) {
            logger.error("解密失败：加密密码值：{}", encryptedPassword, e);
            throw new RuntimeException("密码解密失败", e);
        }
    }
}
