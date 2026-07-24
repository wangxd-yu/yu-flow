package org.yu.flow.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.util.JwtTokenUtil;

/**
 * 启动时注入 JWT 密钥；在 fail-on-insecure-defaults 开启时拒绝历史默认密钥。
 */
@Slf4j
@Component
@Order(50)
public class JwtSecurityInitializer implements ApplicationRunner {

    public static final String LEGACY_AES_DEFAULT = "flow-secure-keys";

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Override
    public void run(ApplicationArguments args) {
        YuFlowProperties.Security security = yuFlowProperties.getSecurity();
        String secret = security != null ? security.getJwtSecretKey() : null;
        long expire = security != null ? security.getJwtExpireSeconds() : 7200L;
        JwtTokenUtil.init(secret, expire);

        boolean failClosed = security == null || security.isFailOnInsecureDefaults();
        if (JwtTokenUtil.isUsingLegacyDefaultSecret()) {
            String msg = "[JWT] 正在使用历史默认密钥（不安全）。请设置环境变量 YU_FLOW_JWT_SECRET "
                    + "或 yu.flow.security.jwt-secret-key（多节点须一致）。fingerprint="
                    + JwtTokenUtil.getSecretKeyFingerprint();
            if (failClosed) {
                throw new IllegalStateException(msg + "；或设 YU_FLOW_FAIL_ON_INSECURE_DEFAULTS=false（仅本地）");
            }
            log.warn(msg);
        } else if (secret == null || secret.isBlank() || secret.trim().length() < 32) {
            String msg = "[JWT] 密钥过弱（须至少 32 字符）。请设置 YU_FLOW_JWT_SECRET / yu.flow.security.jwt-secret-key";
            if (failClosed) {
                throw new IllegalStateException(msg + "；或设 YU_FLOW_FAIL_ON_INSECURE_DEFAULTS=false（仅本地）");
            }
            log.warn(msg);
        } else {
            log.info("[JWT] 管理端 Token 密钥已加载（{}），expireSeconds={}",
                    JwtTokenUtil.getSecretKeyFingerprint(), expire);
        }

        String aes = security == null ? null : security.getAesSecretKey();
        if (aes == null || aes.isBlank() || LEGACY_AES_DEFAULT.equals(aes)) {
            String msg = "[AES] 密钥仍为默认值或未配置。请设置 YU_FLOW_AES_SECRET / yu.flow.security.aes-secret-key";
            if (failClosed) {
                throw new IllegalStateException(msg + "；或设 YU_FLOW_FAIL_ON_INSECURE_DEFAULTS=false（仅本地）");
            }
            log.warn(msg);
        }

        String adminPwd = yuFlowProperties.getPassword();
        if ("123456".equals(adminPwd)) {
            String msg = "[ADMIN] yu.flow.password 仍为弱默认 123456。请设置 YU_FLOW_ADMIN_PASSWORD";
            if (failClosed) {
                throw new IllegalStateException(msg + "；或设 YU_FLOW_FAIL_ON_INSECURE_DEFAULTS=false（仅本地）");
            }
            log.warn(msg);
        }
    }
}
