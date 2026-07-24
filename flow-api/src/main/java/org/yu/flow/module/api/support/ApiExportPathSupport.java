package org.yu.flow.module.api.support;

import cn.hutool.core.util.StrUtil;

/**
 * 对外 Excel 导出 path：业务 URL + 固定后缀 {@code /export}
 */
public final class ApiExportPathSupport {

    public static final String EXPORT_SUFFIX = "/export";

    private ApiExportPathSupport() {
    }

    /** @return 剥离 /export 后的业务 path；非导出请求返回 null */
    public static String stripExportSuffix(String requestPath) {
        if (StrUtil.isBlank(requestPath)) {
            return null;
        }
        String path = requestPath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith(EXPORT_SUFFIX)) {
            return null;
        }
        if (path.equals(EXPORT_SUFFIX)) {
            return "/";
        }
        String base = path.substring(0, path.length() - EXPORT_SUFFIX.length());
        return StrUtil.isBlank(base) ? "/" : base;
    }

    public static boolean isExportPath(String requestPath) {
        return stripExportSuffix(requestPath) != null;
    }

    public static boolean urlEndsWithExportSuffix(String apiUrl) {
        if (StrUtil.isBlank(apiUrl)) {
            return false;
        }
        String u = apiUrl.trim();
        while (u.endsWith("/") && u.length() > 1) {
            u = u.substring(0, u.length() - 1);
        }
        return u.toLowerCase().endsWith(EXPORT_SUFFIX);
    }
}
