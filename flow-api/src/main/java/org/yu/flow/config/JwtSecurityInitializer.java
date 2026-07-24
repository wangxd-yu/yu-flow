package org.yu.flow.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.util.JwtTokenUtil;

/**
 * 启动时注入 JWT 密钥，并提示勿使用历史默认值。
 */
@Slf4j
@Component
@Order(50)
public class JwtSecurityInitializer implements ApplicationRunner {

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Override
    public void run(ApplicationArguments args) {
        YuFlowProperties.Security security = yuFlowProperties.getSecurity();
        String secret = security != null ? security.getJwtSecretKey() : null;
        long expire = security != null ? security.getJwtExpireSeconds() : 7200L;
        JwtTokenUtil.init(secret, expire);
        if (JwtTokenUtil.isUsingLegacyDefaultSecret()) {
            log.warn("[JWT] 正在使用历史默认密钥（不安全）。请设置环境变量 YU_FLOW_JWT_SECRET "
                    + "或 yu.flow.security.jwt-secret-key（多节点须一致）。fingerprint={}",
                    JwtTokenUtil.getSecretKeyFingerprint());
        } else {
            log.info("[JWT] 管理端 Token 密钥已加载（{}），expireSeconds={}",
                    JwtTokenUtil.getSecretKeyFingerprint(), expire);
        }
    }
}
