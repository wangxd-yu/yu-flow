package org.yu.flow.module.api.security;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.exception.FlowException;
import org.yu.flow.util.FlowObjectMapperUtil;

/**
 * 入站 authMode 产品化约束：默认禁止接口覆盖为 {@code NONE}（匿名可调）。
 * 紧急场景可设 {@code yu.flow.security.allow-ingress-auth-none=true}。
 */
public final class ApiSecurityConfigGuard {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    private ApiSecurityConfigGuard() {
    }

    public static boolean isExplicitNone(String securityConfigJson) {
        if (StrUtil.isBlank(securityConfigJson)) {
            return false;
        }
        try {
            ApiSecurityConfig cfg = MAPPER.readValue(securityConfigJson.trim(), ApiSecurityConfig.class);
            return cfg != null && "NONE".equalsIgnoreCase(StrUtil.trim(cfg.getAuthMode()));
        } catch (Exception e) {
            return false;
        }
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
}
