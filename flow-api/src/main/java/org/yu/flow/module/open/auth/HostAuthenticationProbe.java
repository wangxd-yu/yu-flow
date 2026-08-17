package org.yu.flow.module.open.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 宿主登录态探测 SPI（JAR 集成可选）。
 *
 * <p>调用场景：</p>
 * <ul>
 *   <li>已发布业务 API：{@code yu.flow.open.require-host-auth}（ingress 关闭时）</li>
 *   <li>管理端 {@code /flow-api/**}：{@code yu.flow.security.management-require-host-auth}
 *       （默认 false；开启后在管理端 JWT 通过后再校验）</li>
 * </ul>
 *
 * <p>默认 Bean 校验管理端 JWT（见 {@code HostAuthenticationProbeConfiguration}）；
 * 嵌入宿主时请覆盖为本系统 Session / SecurityContext 探测，以形成双层鉴权。
 * 不提供宿主登录后静默换发 Flow JWT。</p>
 */
@FunctionalInterface
public interface HostAuthenticationProbe {

    /**
     * @return true 表示宿主已认可该请求（已登录 / 已放行）
     */
    boolean isAuthenticated(HttpServletRequest request);
}
