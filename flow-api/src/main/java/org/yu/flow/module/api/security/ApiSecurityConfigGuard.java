package org.yu.flow.module.api.security;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.CallerPolicy;
import org.yu.flow.module.host.CallerPolicyMatcher;
import org.yu.flow.util.FlowObjectMapperUtil;

/**
 * 入站 securityConfig 产品化约束。
 */
public final class ApiSecurityConfigGuard {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    private ApiSecurityConfigGuard() {
    }

    public static ApiSecurityConfig parse(String securityConfigJson) {
        if (StrUtil.isBlank(securityConfigJson)) {
            return null;
        }
        try {
            return MAPPER.readValue(securityConfigJson.trim(), ApiSecurityConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isExplicitNone(String securityConfigJson) {
        ApiSecurityConfig cfg = parse(securityConfigJson);
        return cfg != null && "NONE".equalsIgnoreCase(StrUtil.trim(cfg.getAuthMode()));
    }

    public static void assertAuthModeAllowed(String securityConfigJson, boolean allowNone) {
        if (!isExplicitNone(securityConfigJson)) {
            return;
        }
        if (!allowNone) {
            throw new FlowException("INGRESS_AUTH_NONE_FORBIDDEN",
                    "禁止将接口鉴权设为 NONE（匿名可调）。如需临时放开，请设置 "
                            + "yu.flow.security.allow-ingress-auth-none=true / YU_FLOW_ALLOW_INGRESS_AUTH_NONE=true");
        }
    }

    /**
     * 匿名接口不得启用调用方策略；OPEN 可保存策略字段（运行时忽略匹配，便于切回 HOST）。
     */
    public static void assertCallerPolicyAllowed(String securityConfigJson) {
        ApiSecurityConfig cfg = parse(securityConfigJson);
        if (cfg == null || cfg.getCallerPolicy() == null || !cfg.getCallerPolicy().isEnabled()) {
            return;
        }
        if ("NONE".equalsIgnoreCase(StrUtil.trim(cfg.getAuthMode()))) {
            throw new FlowException("INGRESS_CALLER_POLICY_INVALID",
                    "authMode=NONE（匿名）时不能启用 callerPolicy；请改用 HOST 并配置调用方策略");
        }
        try {
            CallerPolicyMatcher.assertMatchValid(cfg.getCallerPolicy());
        } catch (IllegalArgumentException e) {
            throw new FlowException("INGRESS_CALLER_POLICY_INVALID", e.getMessage());
        }
    }
}
