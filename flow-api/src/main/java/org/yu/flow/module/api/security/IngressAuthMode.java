package org.yu.flow.module.api.security;

import cn.hutool.core.util.StrUtil;

/**
 * 入站鉴权模式（生效值，不含 INHERIT）。
 */
public enum IngressAuthMode {
    NONE,
    HOST,
    OPEN;

    public static IngressAuthMode from(String raw) {
        if (StrUtil.isBlank(raw)) {
            return NONE;
        }
        try {
            return IngressAuthMode.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return NONE;
        }
    }
}
