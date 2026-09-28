package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 发布包签名（HMAC-SHA256，对 manifest.json 签名；manifest 含其余文件摘要，因此覆盖整包）。
 */
@Component
public class ReleaseSigning {

    public static final String VERIFIED = "VERIFIED";
    public static final String UNSIGNED = "UNSIGNED";
    public static final String INVALID = "INVALID";
    /** 包带签名但本环境未配置密钥，无法校验 */
    public static final String UNVERIFIABLE = "UNVERIFIABLE";
    /** 双方都未启用签名 */
    public static final String DISABLED = "DISABLED";

    @Resource
    private YuFlowProperties yuFlowProperties;
    @Resource
    private ReleaseEnvironment releaseEnvironment;

    /** 签名用的当前密钥；未配置返回 null */
    public String key() {
        YuFlowProperties.Release release = release();
        String key = release == null ? null : release.getSigningKey();
        return StrUtil.isBlank(key) ? null : key;
    }

    /** 校验时认可的密钥：当前密钥 + 轮换期的旧密钥 */
    List<String> verifyKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (key() != null) {
            keys.add(key());
        }
        YuFlowProperties.Release release = release();
        if (release != null && release.getPreviousSigningKeys() != null) {
            release.getPreviousSigningKeys().stream().filter(StrUtil::isNotBlank).map(String::trim).forEach(keys::add);
        }
        return new ArrayList<>(keys);
    }

    /**
     * 本实例导入时是否必须带有效签名：显式配置优先，否则 PROD 或开启了编辑锁时必须签名。
     */
    public boolean required() {
        YuFlowProperties.Release release = release();
        if (release != null && release.getRequireSignature() != null) {
            return release.getRequireSignature();
        }
        return "PROD".equals(releaseEnvironment.configured()) || (release != null && release.isLockAssetEditing());
    }

    public static String sign(byte[] content, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content));
        } catch (Exception e) {
            throw new IllegalStateException("发布包签名失败", e);
        }
    }

    /** 本环境视角的签名状态 */
    public String status(byte[] manifest, String signature) {
        List<String> keys = verifyKeys();
        if (keys.isEmpty()) {
            return signature == null ? DISABLED : UNVERIFIABLE;
        }
        if (signature == null) {
            return UNSIGNED;
        }
        byte[] actual = signature.trim().getBytes(StandardCharsets.UTF_8);
        for (String key : keys) {
            if (MessageDigest.isEqual(sign(manifest, key).getBytes(StandardCharsets.UTF_8), actual)) {
                return VERIFIED;
            }
        }
        return INVALID;
    }

    private YuFlowProperties.Release release() {
        return yuFlowProperties == null ? null : yuFlowProperties.getRelease();
    }
}
