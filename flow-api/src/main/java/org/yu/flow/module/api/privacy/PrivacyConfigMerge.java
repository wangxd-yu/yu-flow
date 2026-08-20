package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.module.host.HostPrivacyProfile;
import org.yu.flow.module.host.PrivacyAccessRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 隐私策略字段级合并：子覆盖父，null 继续向上。
 */
public final class PrivacyConfigMerge {

    private PrivacyConfigMerge() {
    }

    public static ApiPrivacyConfig empty() {
        return new ApiPrivacyConfig();
    }

    /**
     * 将 layer 填入 merged 中仍为空的字段。
     *
     * @return layer.inherit == false 时 true，调用方应停止向上
     */
    public static boolean overlay(ApiPrivacyConfig merged, ApiPrivacyConfig layer) {
        if (layer == null) {
            return false;
        }
        if (merged.getEnabled() == null && layer.getEnabled() != null) {
            merged.setEnabled(layer.getEnabled());
        }
        if (StrUtil.isBlank(merged.getProfileId()) && StrUtil.isNotBlank(layer.getProfileId())) {
            merged.setProfileId(layer.getProfileId().trim());
        }
        if (StrUtil.isBlank(merged.getFieldSuffix()) && StrUtil.isNotBlank(layer.getFieldSuffix())) {
            merged.setFieldSuffix(layer.getFieldSuffix().trim());
        }
        if (isBlankList(merged.getExtraFields()) && !isBlankList(layer.getExtraFields())) {
            merged.setExtraFields(new ArrayList<>(layer.getExtraFields()));
        }
        if (merged.getStripSuffix() == null && layer.getStripSuffix() != null) {
            merged.setStripSuffix(layer.getStripSuffix());
        }
        if (merged.getAtRest() == null && layer.getAtRest() != null) {
            merged.setAtRest(copyAtRest(layer.getAtRest()));
        } else if (merged.getAtRest() != null && layer.getAtRest() != null) {
            if (StrUtil.isBlank(merged.getAtRest().getAlg()) && StrUtil.isNotBlank(layer.getAtRest().getAlg())) {
                merged.getAtRest().setAlg(layer.getAtRest().getAlg());
            }
            if (StrUtil.isBlank(merged.getAtRest().getKeyId()) && StrUtil.isNotBlank(layer.getAtRest().getKeyId())) {
                merged.getAtRest().setKeyId(layer.getAtRest().getKeyId());
            }
        }
        if (isBlankMap(merged.getMask()) && !isBlankMap(layer.getMask())) {
            merged.setMask(copyMask(layer.getMask()));
        }
        if (StrUtil.isBlank(merged.getOnDecryptFail()) && StrUtil.isNotBlank(layer.getOnDecryptFail())) {
            merged.setOnDecryptFail(layer.getOnDecryptFail().trim());
        }
        if (merged.getRules() == null && layer.getRules() != null) {
            merged.setRules(copyAccessRules(layer.getRules()));
        }
        if (merged.getMaskRules() == null && layer.getMaskRules() != null) {
            merged.setMaskRules(copyRules(layer.getMaskRules()));
        }
        return Boolean.FALSE.equals(layer.getInherit());
    }

    public static EffectivePrivacy toEffective(ApiPrivacyConfig merged, ApiPrivacyConfig systemDefaults) {
        return toEffective(merged, systemDefaults, null);
    }

    public static EffectivePrivacy toEffective(ApiPrivacyConfig merged, ApiPrivacyConfig systemDefaults,
                                               HostPrivacyProfile profile) {
        applyProfileDefaults(merged, profile);
        overlay(merged, systemDefaults);
        boolean enabled = Boolean.TRUE.equals(merged.getEnabled());
        String suffix = StrUtil.blankToDefault(merged.getFieldSuffix(), ApiPrivacyConfig.DEFAULT_SUFFIX);
        Set<String> extras = new LinkedHashSet<>();
        if (merged.getExtraFields() != null) {
            for (String f : merged.getExtraFields()) {
                if (StrUtil.isNotBlank(f)) {
                    extras.add(f.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        boolean strip = merged.getStripSuffix() == null || merged.getStripSuffix();
        String alg = merged.getAtRest() != null && StrUtil.isNotBlank(merged.getAtRest().getAlg())
                ? merged.getAtRest().getAlg().trim() : ApiPrivacyConfig.ALG_SM4;
        String keyId = merged.getAtRest() != null && StrUtil.isNotBlank(merged.getAtRest().getKeyId())
                ? merged.getAtRest().getKeyId().trim() : ApiPrivacyConfig.KEY_DEFAULT;
        Map<String, List<String>> mask = mergeMask(defaultMask(), merged.getMask());
        String fail = StrUtil.blankToDefault(merged.getOnDecryptFail(), ApiPrivacyConfig.FAIL_MASK);

        String profileId = merged.getProfileId();
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.fromAlg(alg);
        String decryptKey = null;
        // 脱敏长什么样只跟界面上能看到的规则走；都空则只留第一位。
        List<PrivacyMaskRule> rules = List.of();
        if (merged.getMaskRules() != null && !merged.getMaskRules().isEmpty()) {
            rules = copyRules(merged.getMaskRules());
        } else if (profile != null && profile.getRules() != null && !profile.getRules().isEmpty()) {
            rules = copyRules(profile.getRules());
        }
        if (profile != null) {
            profileId = profile.getId();
            spec = PrivacyDecryptSpec.fromProfile(profile);
            decryptKey = StrUtil.trim(profile.getDecryptKey());
            if (StrUtil.isBlank(decryptKey)) {
                decryptKey = null;
            }
        }
        return new EffectivePrivacy(enabled, suffix, extras, strip, alg, keyId, mask, fail,
                profileId, spec, decryptKey, rules)
                .withAccessRules(copyAccessRules(merged.getRules()));
    }

    public static ApiPrivacyConfig systemDefaults() {
        ApiPrivacyConfig cfg = new ApiPrivacyConfig();
        cfg.setEnabled(false);
        cfg.setInherit(true);
        cfg.setFieldSuffix(ApiPrivacyConfig.DEFAULT_SUFFIX);
        cfg.setStripSuffix(true);
        ApiPrivacyConfig.AtRest atRest = new ApiPrivacyConfig.AtRest();
        atRest.setAlg(ApiPrivacyConfig.ALG_SM4);
        atRest.setKeyId(ApiPrivacyConfig.KEY_DEFAULT);
        cfg.setAtRest(atRest);
        cfg.setMask(defaultMask());
        cfg.setOnDecryptFail(ApiPrivacyConfig.FAIL_MASK);
        return cfg;
    }

    /** 目录/接口别名与系统默认按类型并集，避免只写 loginPhone 时丢掉 name/idCard。 */
    public static Map<String, List<String>> mergeMask(Map<String, List<String>> defaults,
                                                      Map<String, List<String>> overlay) {
        Map<String, List<String>> out = copyMask(defaults == null ? Map.of() : defaults);
        if (overlay == null || overlay.isEmpty()) {
            return out;
        }
        overlay.forEach((type, names) -> {
            if (StrUtil.isBlank(type) || names == null || names.isEmpty()) {
                return;
            }
            LinkedHashSet<String> union = new LinkedHashSet<>();
            List<String> existing = out.get(type);
            if (existing != null) {
                union.addAll(existing);
            }
            for (String name : names) {
                if (StrUtil.isNotBlank(name)) {
                    union.add(name.trim());
                }
            }
            out.put(type.trim(), new ArrayList<>(union));
        });
        return out;
    }

    public static Map<String, List<String>> defaultMask() {
        Map<String, List<String>> mask = new LinkedHashMap<>();
        mask.put(PrivacyMasker.PHONE, List.of("phone", "mobile", "tel", "手机"));
        mask.put(PrivacyMasker.NAME, List.of("name", "realName", "userName", "姓名"));
        mask.put(PrivacyMasker.ID_CARD, List.of("idCard", "idNo", "certNo", "id_no", "身份证"));
        return mask;
    }

    private static void applyProfileDefaults(ApiPrivacyConfig merged, HostPrivacyProfile profile) {
        if (merged == null || profile == null) {
            return;
        }
        if (StrUtil.isBlank(merged.getFieldSuffix()) && StrUtil.isNotBlank(profile.getFieldSuffix())) {
            merged.setFieldSuffix(profile.getFieldSuffix().trim());
        }
        if (isBlankList(merged.getExtraFields()) && !isBlankList(profile.getExtraFields())) {
            merged.setExtraFields(new ArrayList<>(profile.getExtraFields()));
        }
        if (merged.getStripSuffix() == null && profile.getStripSuffix() != null) {
            merged.setStripSuffix(profile.getStripSuffix());
        }
    }

    private static List<PrivacyAccessRule> copyAccessRules(List<PrivacyAccessRule> src) {
        if (src == null) {
            return null;
        }
        List<PrivacyAccessRule> out = new ArrayList<>();
        for (PrivacyAccessRule r : src) {
            if (r == null) {
                continue;
            }
            PrivacyAccessRule c = new PrivacyAccessRule();
            c.setName(r.getName());
            c.setPrincipals(r.getPrincipals());
            c.setMatch(r.getMatch());
            c.setUserTypes(r.getUserTypes() == null ? new ArrayList<>() : new ArrayList<>(r.getUserTypes()));
            c.setRoles(r.getRoles() == null ? new ArrayList<>() : new ArrayList<>(r.getRoles()));
            c.setPermissions(r.getPermissions() == null ? new ArrayList<>() : new ArrayList<>(r.getPermissions()));
            c.setUserIds(r.getUserIds() == null ? new ArrayList<>() : new ArrayList<>(r.getUserIds()));
            c.setDeptIds(r.getDeptIds() == null ? new ArrayList<>() : new ArrayList<>(r.getDeptIds()));
            c.setDeptIncludeChildren(r.getDeptIncludeChildren());
            c.setPrivacy(r.getPrivacy());
            c.setFields(r.getFields() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(r.getFields()));
            out.add(c);
        }
        return out;
    }

    private static List<PrivacyMaskRule> copyRules(List<PrivacyMaskRule> src) {
        List<PrivacyMaskRule> out = new ArrayList<>();
        for (PrivacyMaskRule r : src) {
            if (r == null) {
                continue;
            }
            PrivacyMaskRule c = new PrivacyMaskRule();
            c.setId(r.getId());
            c.setAliases(r.getAliases() == null ? new ArrayList<>() : new ArrayList<>(r.getAliases()));
            c.setMatchMode(r.getMatchMode());
            c.setMethod(r.getMethod());
            c.setKeepHead(r.getKeepHead());
            c.setKeepTail(r.getKeepTail());
            c.setMaskLen(r.getMaskLen());
            c.setMaskChar(r.getMaskChar());
            out.add(c);
        }
        return out;
    }

    private static ApiPrivacyConfig.AtRest copyAtRest(ApiPrivacyConfig.AtRest src) {
        ApiPrivacyConfig.AtRest copy = new ApiPrivacyConfig.AtRest();
        copy.setAlg(src.getAlg());
        copy.setKeyId(src.getKeyId());
        return copy;
    }

    private static Map<String, List<String>> copyMask(Map<String, List<String>> src) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, v == null ? new ArrayList<>() : new ArrayList<>(v)));
        return out;
    }

    private static boolean isBlankList(List<String> list) {
        return list == null || list.isEmpty();
    }

    private static boolean isBlankMap(Map<String, List<String>> map) {
        return map == null || map.isEmpty();
    }
}
