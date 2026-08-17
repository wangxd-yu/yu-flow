package org.yu.flow.module.open.auth;

import lombok.Getter;

@Getter
public class OpenAuthException extends RuntimeException {

    private final int httpStatus;
    private final String code;

    public OpenAuthException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static OpenAuthException missing() {
        return new OpenAuthException(401, "OPEN_AUTH_MISSING", "缺少开放平台凭证");
    }

    public static OpenAuthException invalid() {
        return new OpenAuthException(401, "OPEN_AUTH_INVALID", "开放平台鉴权失败");
    }

    public static OpenAuthException expired() {
        return new OpenAuthException(401, "OPEN_AUTH_EXPIRED", "凭证或签名已过期");
    }

    public static OpenAuthException denied(String msg) {
        return new OpenAuthException(403, "OPEN_AUTH_DENIED", msg);
    }

    public static OpenAuthException ipDenied() {
        return new OpenAuthException(403, "OPEN_AUTH_IP_DENIED", "IP 不在白名单内");
    }

    public static OpenAuthException rateLimited() {
        return new OpenAuthException(429, "OPEN_RATE_LIMITED", "调用频率超过限制");
    }

    public static OpenAuthException methodDenied(String msg) {
        return new OpenAuthException(403, "OPEN_AUTH_METHOD_DENIED", msg);
    }

    public static OpenAuthException hostAuthRequired() {
        return new OpenAuthException(401, "OPEN_HOST_AUTH_REQUIRED", "需要宿主登录后访问该接口");
    }

    /** 管理端双层鉴权：宿主登录探测未通过 */
    public static OpenAuthException managementHostAuthRequired() {
        return new OpenAuthException(401, "FLOW_HOST_AUTH_REQUIRED",
                "需要宿主登录后访问管理端接口");
    }
}
