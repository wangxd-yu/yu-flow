package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;

import java.util.Locale;

/**
 * 本实例所属环境（{@code yu.flow.release.current-env}）。
 *
 * <p>配置后请求里传入的 envCode 一律以本实例环境为准：否则生产实例上选 DEV 即可绕过生产门禁，
 * 回归运行也会记在别的环境名下，导致门禁永远查不到通过记录。</p>
 */
@Component
public class ReleaseEnvironment {

    @Resource
    private YuFlowProperties yuFlowProperties;

    /** 已配置的本实例环境；未配置返回 null */
    public String configured() {
        String env = yuFlowProperties.getRelease() == null ? null : yuFlowProperties.getRelease().getCurrentEnv();
        return StrUtil.isBlank(env) ? null : env.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isLocked() {
        return configured() != null;
    }

    /** 门禁、回归、审计使用的生效环境 */
    public String resolve(String requestedEnvCode) {
        String locked = configured();
        return locked != null ? locked : RegressionSecurity.normalizeEnvCode(requestedEnvCode);
    }

    /** 本实例环境；未配置时按 DEV 处理 */
    public String current() {
        return resolve(null);
    }
}
