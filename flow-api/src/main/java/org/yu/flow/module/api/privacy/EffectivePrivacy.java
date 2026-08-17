package org.yu.flow.module.api.privacy;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 合并后的出站隐私策略（接口 → 目录链 → 宿主机方案 → 系统默认）。
 */
public final class EffectivePrivacy {

    private final boolean enabled;
    private final String fieldSuffix;
    private final Set<String> extraFieldsLower;
    private final boolean stripSuffix;
    private final String atRestAlg;
    private final String atRestKeyId;
    private final Map<String, List<String>> maskAliases;
    private final String onDecryptFail;
    private final String profileId;
    private final String decryptAlg;
    private final PrivacyDecryptSpec decryptSpec;
    private final String decryptKey;
    private final List<PrivacyMaskRule> maskRules;

    public EffectivePrivacy(boolean enabled, String fieldSuffix, Set<String> extraFieldsLower,
                            boolean stripSuffix, String atRestAlg, String atRestKeyId,
                            Map<String, List<String>> maskAliases, String onDecryptFail) {
        this(enabled, fieldSuffix, extraFieldsLower, stripSuffix, atRestAlg, atRestKeyId,
                maskAliases, onDecryptFail, null, PrivacyDecryptSpec.fromAlg(atRestAlg), null,
                PrivacyMasker.rulesFromAliasMap(maskAliases));
    }

    public EffectivePrivacy(boolean enabled, String fieldSuffix, Set<String> extraFieldsLower,
                            boolean stripSuffix, String atRestAlg, String atRestKeyId,
                            Map<String, List<String>> maskAliases, String onDecryptFail,
                            String profileId, String decryptAlg, String decryptKey,
                            List<PrivacyMaskRule> maskRules) {
        this(enabled, fieldSuffix, extraFieldsLower, stripSuffix, atRestAlg, atRestKeyId,
                maskAliases, onDecryptFail, profileId,
                PrivacyDecryptSpec.fromAlg(decryptAlg != null ? decryptAlg : atRestAlg),
                decryptKey, maskRules);
    }

    public EffectivePrivacy(boolean enabled, String fieldSuffix, Set<String> extraFieldsLower,
                            boolean stripSuffix, String atRestAlg, String atRestKeyId,
                            Map<String, List<String>> maskAliases, String onDecryptFail,
                            String profileId, PrivacyDecryptSpec decryptSpec, String decryptKey,
                            List<PrivacyMaskRule> maskRules) {
        this.enabled = enabled;
        this.fieldSuffix = fieldSuffix == null ? ApiPrivacyConfig.DEFAULT_SUFFIX : fieldSuffix;
        this.extraFieldsLower = extraFieldsLower == null ? Set.of() : extraFieldsLower;
        this.stripSuffix = stripSuffix;
        this.atRestAlg = atRestAlg;
        this.atRestKeyId = atRestKeyId;
        this.maskAliases = maskAliases == null ? Map.of() : maskAliases;
        this.onDecryptFail = onDecryptFail == null ? ApiPrivacyConfig.FAIL_MASK : onDecryptFail;
        this.profileId = profileId;
        this.decryptSpec = decryptSpec == null
                ? PrivacyDecryptSpec.fromAlg(atRestAlg)
                : decryptSpec.normalize();
        this.decryptAlg = this.decryptSpec.getFamily();
        this.decryptKey = decryptKey;
        this.maskRules = maskRules == null ? List.of() : List.copyOf(maskRules);
    }

    public static EffectivePrivacy disabled() {
        return new EffectivePrivacy(false, ApiPrivacyConfig.DEFAULT_SUFFIX, Set.of(), true,
                ApiPrivacyConfig.ALG_SM4, ApiPrivacyConfig.KEY_DEFAULT, Map.of(), ApiPrivacyConfig.FAIL_MASK);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getFieldSuffix() {
        return fieldSuffix;
    }

    public boolean isStripSuffix() {
        return stripSuffix;
    }

    public String getAtRestAlg() {
        return atRestAlg;
    }

    public String getAtRestKeyId() {
        return atRestKeyId;
    }

    public Map<String, List<String>> getMaskAliases() {
        return Collections.unmodifiableMap(maskAliases);
    }

    public String getOnDecryptFail() {
        return onDecryptFail;
    }

    public String getProfileId() {
        return profileId;
    }

    public PrivacyDecryptSpec getDecryptSpec() {
        return decryptSpec;
    }

    public String getDecryptAlg() {
        return decryptAlg;
    }

    public String getDecryptKey() {
        return decryptKey;
    }

    public List<PrivacyMaskRule> getMaskRules() {
        return maskRules;
    }

    public boolean isPrivacyField(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String raw = key.trim();
        String suffix = fieldSuffix == null ? "" : fieldSuffix;
        if (!suffix.isEmpty() && raw.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return extraFieldsLower.contains(raw.toLowerCase(Locale.ROOT));
    }

    public String outputKey(String key) {
        if (key == null || !stripSuffix) {
            return key;
        }
        String suffix = fieldSuffix == null ? "" : fieldSuffix;
        if (suffix.isEmpty()) {
            return key;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        String suffixLower = suffix.toLowerCase(Locale.ROOT);
        if (lower.endsWith(suffixLower) && key.length() > suffix.length()) {
            return key.substring(0, key.length() - suffix.length());
        }
        return key;
    }
}
