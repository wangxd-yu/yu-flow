package org.yu.flow.module.open.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

/**
 * 开放平台生产安全提示（不阻断启动）。
 */
@Slf4j
@Component
@Order(200)
public class OpenPlatformSecurityStartupCheck implements ApplicationRunner {

    @Resource
    private YuFlowProperties yuFlowProperties;
    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;

    @Override
    public void run(ApplicationArguments args) {
        if (!yuFlowRuntimeSettings.isOpenEnabled()) {
            return;
        }
        if (yuFlowRuntimeSettings.isOpenAllowPlainSecret()) {
            log.warn("[OpenPlatform] allow-plain-secret=true：允许明文 Secret 头，"
                    + "生产环境请在系统配置 OPEN_ALLOW_PLAIN_SECRET 或 yml 中设为 false（仅 HMAC）");
        }
        String aes = yuFlowProperties.getSecurity() == null
                ? null : yuFlowProperties.getSecurity().getAesSecretKey();
        if (aes == null || "flow-secure-keys".equals(aes) || aes.isBlank()) {
            log.warn("[OpenPlatform] AES 密钥仍为默认值或未配置，凭证加密不安全。"
                    + "请设置 yu.flow.security.aes-secret-key（多节点须一致）");
        }
        if (!yuFlowRuntimeSettings.isOpenNonceFailClosed()) {
            log.warn("[OpenPlatform] nonce-fail-closed=false：Redis 故障时可能接受重放请求");
        }
    }
}
