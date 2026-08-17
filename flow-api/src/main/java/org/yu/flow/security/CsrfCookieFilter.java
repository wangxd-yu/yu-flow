package org.yu.flow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Cookie 会话的 CSRF 防护（双提交）。
 * <p>若请求已带 {@code Flow-Authorization} 或 {@code Authorization: Bearer} 头，则视为头鉴权（抗 CSRF），跳过校验。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CsrfCookieFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase(Locale.ROOT);
        if (SAFE.contains(method) || !isFlowApi(request) || isAuthExempt(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 头鉴权：不强制 CSRF（Flow 管理 JWT 或宿主 Authorization Bearer）
        String headerToken = request.getHeader("Flow-Authorization");
        if (headerToken != null && !headerToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (hasBearerAuthorization(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 无 Cookie 会话也不强制（未登录由后续网关处理）
        String cookieToken = AuthCookieSupport.readCookie(request, AuthCookieSupport.TOKEN_COOKIE);
        if (cookieToken == null || cookieToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!AuthCookieSupport.csrfMatches(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"ok\":false,\"code\":403,\"msg\":\"CSRF 校验失败\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isFlowApi(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        return path.startsWith("/flow-api/");
    }

    private static boolean isAuthExempt(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        return "/flow-api/login".equals(path)
                || "/flow-api/login/captcha".equals(path)
                || "/flow-api/login/public-key".equals(path);
    }

    private static boolean hasBearerAuthorization(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.length() <= 7
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        return !authorization.substring(7).isBlank();
    }
}
