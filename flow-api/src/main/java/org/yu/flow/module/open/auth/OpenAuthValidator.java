package org.yu.flow.module.open.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 开放平台鉴权后扩展点（限流 / 配额等）。
 *
 * <p>OSS 默认空实现；Pro 可替换 Bean 注入 QPS/日配额而不改网关主流程。</p>
 */
public interface OpenAuthValidator {

    /**
     * 在凭证校验成功后、接口授权校验前调用。
     *
     * @throws OpenAuthException 如限流（建议 429 / OPEN_RATE_LIMITED）
     */
    void afterAuthenticated(OpenAuthContext ctx, HttpServletRequest request, String realPath, String method);
}
