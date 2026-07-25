package org.yu.flow.module.api.support;

import cn.hutool.core.util.StrUtil;

/**
 * 同名拦截模式常量与归一化。
 */
public final class ApiInterceptMode {

    /** 引擎替换宿主同名接口（短路，不进宿主 Controller） */
    public static final String REPLACE = "REPLACE";
    /** 包裹转发宿主同名接口（观测/可选防护后进宿主） */
    public static final String WRAP = "WRAP";

    public static final String SERVICE_TYPE_HOST = "HOST";

    private ApiInterceptMode() {
    }

    public static String normalize(String raw) {
        if (StrUtil.isBlank(raw)) {
            return REPLACE;
        }
        String v = raw.trim().toUpperCase();
        return WRAP.equals(v) ? WRAP : REPLACE;
    }

    public static boolean isWrap(String raw) {
        return WRAP.equals(normalize(raw));
    }

    public static boolean isReplace(String raw) {
        return !isWrap(raw);
    }
}
