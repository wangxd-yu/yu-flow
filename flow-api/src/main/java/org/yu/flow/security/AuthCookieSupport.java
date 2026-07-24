package org.yu.flow.security;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 管理端会话 Cookie：HttpOnly JWT + 可读 CSRF（双提交）。
 */
public final class AuthCookieSupport {

    public static final String TOKEN_COOKIE = "YU_FLOW_TOKEN";
    public static final String CSRF_COOKIE = "YU_FLOW_CSRF";
    public static final String CSRF_HEADER = "X-Yu-CSRF";

    private AuthCookieSupport() {
    }

    public static String readCookie(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    public static void writeSessionCookies(HttpServletRequest request, HttpServletResponse response,
                                          String rawJwt, int maxAgeSeconds) {
        boolean secure = isSecureRequest(request);
        String path = cookiePath(request);
        int age = Math.max(60, maxAgeSeconds);
        response.addHeader("Set-Cookie", buildSetCookieHeader(
                TOKEN_COOKIE, rawJwt, path, age, true, secure, "Lax"));
        String csrf = IdUtil.fastSimpleUUID();
        response.addHeader("Set-Cookie", buildSetCookieHeader(
                CSRF_COOKIE, csrf, path, age, false, secure, "Lax"));
    }

    public static void clearSessionCookies(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = isSecureRequest(request);
        String path = cookiePath(request);
        response.addHeader("Set-Cookie", buildSetCookieHeader(
                TOKEN_COOKIE, "", path, 0, true, secure, "Lax"));
        response.addHeader("Set-Cookie", buildSetCookieHeader(
                CSRF_COOKIE, "", path, 0, false, secure, "Lax"));
    }

    public static boolean csrfMatches(HttpServletRequest request) {
        String cookie = readCookie(request, CSRF_COOKIE);
        String header = request.getHeader(CSRF_HEADER);
        if (StrUtil.isBlank(cookie) || StrUtil.isBlank(header)) {
            return false;
        }
        return cookie.equals(header);
    }

    private static boolean isSecureRequest(HttpServletRequest request) {
        return request.isSecure()
                || "https".equalsIgnoreCase(StrUtil.blankToDefault(request.getHeader("X-Forwarded-Proto"), ""));
    }

    private static String cookiePath(HttpServletRequest request) {
        // 统一 Path=/ ：兼容 Umi 开发代理（前端无 context-path）与生产 /flow 前缀
        return "/";
    }

    private static String buildSetCookieHeader(String name, String value, String path,
                                               int maxAge, boolean httpOnly, boolean secure, String sameSite) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value == null ? "" : value);
        sb.append("; Path=").append(path);
        sb.append("; Max-Age=").append(Math.max(0, maxAge));
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (secure) {
            sb.append("; Secure");
        }
        sb.append("; SameSite=").append(sameSite);
        return sb.toString();
    }
}
