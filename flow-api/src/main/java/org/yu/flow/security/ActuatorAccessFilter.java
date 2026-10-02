package org.yu.flow.security;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import org.yu.flow.config.YuFlowProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Actuator 端点访问控制。
 *
 * <p>Flow 网关只管 {@code /flow-api/**}，其余路径直接放行给 MVC，不加控制时 {@code /actuator/**} 对外完全开放。这里：
 * <ul>
 *   <li>{@code health} 及其探针子路径匿名可访问（负载均衡 / K8s 探活；配置里已关闭明细，只返回状态）；</li>
 *   <li>其余端点（prometheus、info、端点索引）须带 {@code Authorization: Bearer <yu.flow.observability.metrics-token>}；
 *       未配置令牌时返回 404，相当于关闭。</li>
 * </ul>
 * 使用独立管理端口（{@code management.server.port}）时端点在子上下文里，不经过本过滤器，应只在内网暴露该端口。</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ActuatorAccessFilter extends OncePerRequestFilter {

    private final YuFlowProperties yuFlowProperties;
    private final String basePath;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public ActuatorAccessFilter(YuFlowProperties yuFlowProperties,
                                @Value("${management.endpoints.web.base-path:/actuator}") String basePath) {
        this.yuFlowProperties = yuFlowProperties;
        this.basePath = StrUtil.removeSuffix(basePath, "/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = urlPathHelper.getPathWithinApplication(request);
        return !(path.equals(basePath) || path.startsWith(basePath + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = urlPathHelper.getPathWithinApplication(request);
        String health = basePath + "/health";
        if (path.equals(health) || path.startsWith(health + "/")) {
            chain.doFilter(request, response);
            return;
        }
        String token = yuFlowProperties.getObservability() == null ? null
                : yuFlowProperties.getObservability().getMetricsToken();
        if (StrUtil.isBlank(token)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String auth = request.getHeader("Authorization");
        String presented = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7).trim() : "";
        if (!MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), token.trim().getBytes(StandardCharsets.UTF_8))) {
            response.setHeader("WWW-Authenticate", "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
