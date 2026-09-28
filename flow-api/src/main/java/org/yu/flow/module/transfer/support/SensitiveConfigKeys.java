package org.yu.flow.module.transfer.support;

import java.util.regex.Pattern;

/**
 * 不随发布包迁移的系统配置键：口令、密钥、令牌类配置各环境必须独立维护。
 */
public final class SensitiveConfigKeys {

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(PASSWORD|PASSWD|PWD|SECRET|TOKEN|PRIVATE|CREDENTIAL|ACCESS_?KEY|APP_?KEY|API_?KEY|SM[24]|AES|_KEY$|WEBHOOK)");
    /** 名字里带 TOKEN 等字样但描述的是时长 / 开关，不是密钥本身 */
    private static final Pattern HARMLESS_SUFFIX = Pattern.compile("(?i)(EXPIRE|TTL|TIMEOUT|LENGTH|ENABLED|MODE|SECONDS|MINUTES)$");

    private SensitiveConfigKeys() {
    }

    public static boolean isSensitive(String configKey) {
        if (configKey == null) {
            return true;
        }
        return SENSITIVE.matcher(configKey).find() && !HARMLESS_SUFFIX.matcher(configKey).find();
    }
}
