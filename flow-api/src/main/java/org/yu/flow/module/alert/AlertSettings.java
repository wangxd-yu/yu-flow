package org.yu.flow.module.alert;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;

import jakarta.annotation.Resource;

/**
 * 运行告警系统配置读取。
 */
@Component
public class AlertSettings {

    public static final String ENABLED = "ALERT_ENABLED";
    public static final String WEBHOOK_URL = "ALERT_WEBHOOK_URL";
    public static final String INTERVAL_MINUTES = "ALERT_INTERVAL_MINUTES";
    public static final String TOP_N = "ALERT_TOP_N";
    public static final String WINDOW = "ALERT_WINDOW";
    public static final String MIN_HEALTH = "ALERT_MIN_HEALTH";
    public static final String DEDUP_MINUTES = "ALERT_DEDUP_MINUTES";

    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    public boolean isEnabled() {
        return Boolean.TRUE.equals(sysConfigCacheManager.getBoolConfig(ENABLED, false));
    }

    public String getWebhookUrl() {
        return StrUtil.trim(sysConfigCacheManager.getStringConfig(WEBHOOK_URL, ""));
    }

    public int getIntervalMinutes() {
        Integer v = sysConfigCacheManager.getIntConfig(INTERVAL_MINUTES, 15);
        return Math.max(1, v == null ? 15 : v);
    }

    public int getTopN() {
        Integer v = sysConfigCacheManager.getIntConfig(TOP_N, 10);
        int n = v == null ? 10 : v;
        return Math.min(100, Math.max(1, n));
    }

    public String getWindow() {
        String w = sysConfigCacheManager.getStringConfig(WINDOW, "24h");
        return StrUtil.blankToDefault(w, "24h");
    }

    /** error | warn（warn 含 error） */
    public String getMinHealth() {
        String h = sysConfigCacheManager.getStringConfig(MIN_HEALTH, "error");
        return "warn".equalsIgnoreCase(StrUtil.trim(h)) ? "warn" : "error";
    }

    public int getDedupMinutes() {
        Integer v = sysConfigCacheManager.getIntConfig(DEDUP_MINUTES, 60);
        return Math.max(1, v == null ? 60 : v);
    }
}
