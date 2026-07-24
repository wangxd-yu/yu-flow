package org.yu.flow.module.open.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 宿主登录态探测 SPI（JAR 集成可选）。
 *
 * <p>在「已发布 API 真实 path」且未带开放平台 AppKey 时，若开启
 * {@code yu.flow.open.require-host-auth}，网关会调用本接口确认宿主已鉴权，
 * 防止宿主误将业务 URL 配成 {@code permitAll} 后被匿名访问。</p>
 *
 * <p>默认实现恒为 {@code true}（宽松）；宿主可提供自己的 Bean 覆盖。</p>
 */
@FunctionalInterface
public interface HostAuthenticationProbe {

    /**
     * @return true 表示宿主已认可该请求（已登录 / 已放行）
     */
    boolean isAuthenticated(HttpServletRequest request);
}
