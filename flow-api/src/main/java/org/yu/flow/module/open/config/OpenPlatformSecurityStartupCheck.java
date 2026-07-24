package org.yu.flow.module.open.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.yu.flow.config.JwtSecurityInitializer;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

/**
 * 开放平台生产安全检查（与 JWT 侧 fail-on-insecure-defaults 对齐）。
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
        boolean failClosed = yuFlowProperties.getSecurity() == null
                || yuFlowProperties.getSecurity().isFailOnInsecureDefaults();

        if (yuFlowRuntimeSettings.isOpenAllowPlainSecret()) {
            String msg = "[OpenPlatform] allow-plain-secret=true：允许明文 Secret 头，生产请关闭";
            if (failClosed) {
                throw new IllegalStateException(msg);
            }
            log.warn(msg);
        }
        String aes = yuFlowProperties.getSecurity() == null
                ? null : yuFlowProperties.getSecurity().getAesSecretKey();
        if (aes == null || JwtSecurityInitializer.LEGACY_AES_DEFAULT.equals(aes) || aes.isBlank()) {
            // JWT initializer 已处理 fail-closed；此处仅补日志
            log.warn("[OpenPlatform] AES 密钥仍为默认值或未配置，凭证加密不安全");
        }
        if (!yuFlowRuntimeSettings.isOpenNonceFailClosed()) {
            log.warn("[OpenPlatform] nonce-fail-closed=false：Redis 故障时可能接受重放请求");
        }
    }
}
