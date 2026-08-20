package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.module.host.PrivacyAccessRule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 当前调用方命中的出站隐私结果：行级 MASK/REVEAL + 可选字段动作。
 */
public final class PrivacyDecision {

    public static final String ACTION_REVEAL = PrivacyAccessRule.FIELD_REVEAL;
    public static final String ACTION_MASK = PrivacyAccessRule.FIELD_MASK;
    public static final String ACTION_DROP = PrivacyAccessRule.FIELD_DROP;

    private final PrivacyClass privacyClass;
    private final Map<String, String> fieldActions;
    private final String matchedRuleName;

    private PrivacyDecision(PrivacyClass privacyClass, Map<String, String> fieldActions, String matchedRuleName) {
        this.privacyClass = privacyClass == null ? PrivacyClass.MASK : privacyClass;
        this.fieldActions = fieldActions == null || fieldActions.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fieldActions));
        this.matchedRuleName = matchedRuleName;
    }

    public static PrivacyDecision mask() {
        return of(PrivacyClass.MASK);
    }

    public static PrivacyDecision of(PrivacyClass privacyClass) {
        return new PrivacyDecision(privacyClass, Map.of(), null);
    }

    public static PrivacyDecision of(PrivacyClass privacyClass, Map<String, String> fieldActions, String matchedRuleName) {
        return new PrivacyDecision(privacyClass, normalizeActions(fieldActions), matchedRuleName);
    }

    public PrivacyClass getPrivacyClass() {
        return privacyClass;
    }

    public Map<String, String> getFieldActions() {
        return fieldActions;
    }

    public String getMatchedRuleName() {
        return matchedRuleName;
    }

    public boolean drops(String outputKey, String rawKey) {
        return ACTION_DROP.equals(actionFor(outputKey, rawKey));
    }

    /**
     * 字段动作；未配置时返回 null，调用方回退行级 {@link #privacyClass}。
     */
    public String actionFor(String outputKey, String rawKey) {
        if (fieldActions.isEmpty()) {
            return null;
        }
        String byOut = lookup(outputKey);
        if (byOut != null) {
            return byOut;
        }
        return lookup(rawKey);
    }

    public PrivacyClass classForField(String outputKey, String rawKey) {
        String action = actionFor(outputKey, rawKey);
        if (ACTION_REVEAL.equals(action)) {
            return PrivacyClass.REVEAL;
        }
        if (ACTION_MASK.equals(action) || ACTION_DROP.equals(action)) {
            return PrivacyClass.MASK;
        }
        return privacyClass;
    }

    /** 响应缓存后缀。明文档由调用方禁止缓存；脱敏档带字段指纹以免串档。 */
    public String cacheSuffix() {
        StringBuilder sb = new StringBuilder(":p").append(privacyClass.name());
        if (!fieldActions.isEmpty()) {
            sb.append(':');
            fieldActions.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(','));
        }
        return sb.toString();
    }

    private String lookup(String key) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        return fieldActions.get(key.trim().toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> normalizeActions(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (StrUtil.isBlank(k) || StrUtil.isBlank(v)) {
                return;
            }
            String action = v.trim().toUpperCase(Locale.ROOT);
            if (!ACTION_REVEAL.equals(action) && !ACTION_MASK.equals(action) && !ACTION_DROP.equals(action)) {
                return;
            }
            for (String part : k.split("[,，;；]+")) {
                if (StrUtil.isBlank(part)) {
                    continue;
                }
                out.put(part.trim().toLowerCase(Locale.ROOT), action);
            }
        });
        return out;
    }
}
