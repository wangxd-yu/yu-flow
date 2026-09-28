package org.yu.flow.module.transfer.support;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 哪些系统配置可以随发布包迁移。
 *
 * <p>口令 / 密钥类配置各环境独立维护；平台安全、入站防护、开放平台、网关、邮件、隐私类配置
 * 同样与环境绑定，随包导入等于让持有导入权限的人改掉生产的安全策略，一律不迁移。</p>
 *
 * <p>分组既看包里声明的分组，也看目标环境同名配置的分组（包里的分组可以被改写）；
 * 目标环境还没有该配置时再按内置配置键前缀兜底。</p>
 */
public final class ConfigTransferPolicy {

    static final Set<String> PROTECTED_GROUPS = Set.of("SECURITY", "GATEWAY", "OPEN", "INGRESS", "PRIVACY", "MAIL");

    /** 上述分组内置配置的键前缀 */
    private static final Pattern PROTECTED_KEY = Pattern.compile(
            "(?i)^(SYSTEM_PREFIX$|API_TIMEOUT$|TOKEN_|LOGIN_|RBAC_|SCRIPT_|OPEN_|INGRESS_|PRIVACY_|MAIL_)");

    private ConfigTransferPolicy() {
    }

    /**
     * @param groups 包里声明的分组、目标环境同名配置的分组（可为 null）
     * @return 不允许迁移的原因；允许时返回 null
     */
    public static String rejectReason(String configKey, String... groups) {
        if (SensitiveConfigKeys.isSensitive(configKey)) {
            return "口令 / 密钥类配置不随包迁移，请在各环境单独维护";
        }
        for (String group : groups) {
            if (group != null && PROTECTED_GROUPS.contains(group.trim().toUpperCase(Locale.ROOT))) {
                return "「" + group.trim() + "」分组属于环境安全配置，不随包迁移，请在各环境单独维护";
            }
        }
        if (PROTECTED_KEY.matcher(configKey).find()) {
            return "平台安全 / 网关类配置不随包迁移，请在各环境单独维护";
        }
        return null;
    }
}
