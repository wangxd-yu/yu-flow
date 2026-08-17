package org.yu.flow.module.api.security;

import lombok.Getter;
import org.yu.flow.module.open.auth.OpenAuthException;

@Getter
public class IngressException extends RuntimeException {

    private final int httpStatus;
    private final String code;

    public IngressException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static IngressException hostAuthRequired() {
        return new IngressException(401, "INGRESS_HOST_AUTH_REQUIRED", "需要宿主登录后访问该接口");
    }

    public static IngressException callerDenied(String detail) {
        String msg = detail == null || detail.isBlank()
                ? "调用方不满足接口访问策略"
                : detail;
        return new IngressException(403, "INGRESS_CALLER_DENIED", msg);
    }

    public static IngressException ipDenied() {
        return new IngressException(403, "INGRESS_IP_DENIED", "IP 不在白名单内");
    }

    public static IngressException rateLimited() {
        return new IngressException(429, "INGRESS_RATE_LIMITED", "调用频率超过限制");
    }

    public static IngressException apiTimeout(int timeoutMs) {
        return new IngressException(504, "API_TIMEOUT",
                "接口执行超时 (" + timeoutMs + "ms)");
    }

    public static IngressException fromOpen(OpenAuthException e) {
        if (e == null) {
            return new IngressException(401, "INGRESS_AUTH_INVALID", "开放平台鉴权失败");
        }
        String openCode = e.getCode();
        if ("OPEN_AUTH_IP_DENIED".equals(openCode)) {
            return ipDenied();
        }
        if ("OPEN_RATE_LIMITED".equals(openCode)) {
            // 平台级 QPS：保留 OPEN 码语义但统一 INGRESS 前缀
            return new IngressException(429, "INGRESS_RATE_LIMITED", e.getMessage());
        }
        if ("OPEN_HOST_AUTH_REQUIRED".equals(openCode)) {
            return hostAuthRequired();
        }
        String mapped = openCode == null ? "INGRESS_AUTH_INVALID" : openCode.replaceFirst("^OPEN_", "INGRESS_");
        return new IngressException(e.getHttpStatus(), mapped, e.getMessage());
    }
}
