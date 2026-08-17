package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.privacy.PrivacyCryptoService;
import org.yu.flow.module.api.privacy.PrivacyDecryptAlg;
import org.yu.flow.module.api.privacy.PrivacyDecryptSpec;
import org.yu.flow.module.api.privacy.PrivacyMaskRule;
import org.yu.flow.module.api.privacy.PrivacyMasker;
import org.yu.flow.module.host.dto.HostPrivacyProfileViewDTO;
import org.yu.flow.module.host.dto.HostPrivacyProfilesDTO;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 读写 {@code HOST_PRIVACY_PROFILES}，不走通用系统配置页。
 */
@Slf4j
@Component
public class HostPrivacyProfilesStore {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private SysConfigRepository sysConfigRepository;
    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    public HostPrivacyProfiles load() {
        String raw = sysConfigCacheManager.getStringConfig(HostPrivacyProfiles.SETTINGS_KEY, null);
        if (StrUtil.isBlank(raw)) {
            raw = sysConfigRepository.findByConfigKey(HostPrivacyProfiles.SETTINGS_KEY)
                    .map(SysConfigDO::getConfigValue)
                    .orElse(null);
        }
        return parse(raw);
    }

    public HostPrivacyProfile find(String profileId) {
        if (StrUtil.isBlank(profileId)
                || PrivacyDecryptAlg.BUILTIN_PROFILE_ID.equalsIgnoreCase(profileId.trim())) {
            return null;
        }
        String id = profileId.trim();
        for (HostPrivacyProfile p : load().getProfiles()) {
            if (p != null && id.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public HostPrivacyProfiles save(HostPrivacyProfiles incoming) {
        HostPrivacyProfiles merged = mergeKeys(incoming, load());
        normalizeAndValidate(merged);
        String json;
        try {
            json = MAPPER.writeValueAsString(merged);
        } catch (Exception e) {
            throw new FlowException("HOST_PRIVACY_PROFILES_INVALID", "无法序列化隐私方案");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        SysConfigDO entity = sysConfigRepository.findByConfigKey(HostPrivacyProfiles.SETTINGS_KEY)
                .orElseGet(() -> SysConfigDO.builder()
                        .configKey(HostPrivacyProfiles.SETTINGS_KEY)
                        .valueType("JSON")
                        .configGroup("HOST")
                        .remark("宿主隐私解密与脱敏方案（请从「宿主机配置」维护）")
                        .isBuiltin(1)
                        .status(1)
                        .sortOrder(902)
                        .createTime(now)
                        .build());
        entity.setConfigValue(json);
        entity.setUpdateTime(now);
        SysConfigDO saved = sysConfigRepository.save(entity);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sysConfigCacheManager.putLocal(saved);
                }
            });
        } else {
            sysConfigCacheManager.putLocal(saved);
        }
        sysConfigCacheManager.publishRefreshEvent();
        return merged;
    }

    public HostPrivacyProfilesDTO toView(HostPrivacyProfiles source) {
        HostPrivacyProfilesDTO dto = new HostPrivacyProfilesDTO();
        if (source == null || source.getProfiles() == null) {
            return dto;
        }
        List<HostPrivacyProfileViewDTO> views = new ArrayList<>();
        for (HostPrivacyProfile p : source.getProfiles()) {
            if (p == null) {
                continue;
            }
            PrivacyDecryptSpec spec = PrivacyDecryptSpec.fromProfile(p);
            views.add(new HostPrivacyProfileViewDTO()
                    .setId(p.getId())
                    .setName(p.getName())
                    .setDecryptAlg(spec.getFamily())
                    .setDecryptMode(spec.getMode())
                    .setDecryptEncoding(spec.getEncoding())
                    .setDecryptIvMode(spec.getIvMode())
                    .setDecryptIvFixed(spec.getIvFixed())
                    .setDecryptKeySet(StrUtil.isNotBlank(p.getDecryptKey()))
                    .setFieldSuffix(p.getFieldSuffix())
                    .setExtraFields(p.getExtraFields() == null ? new ArrayList<>() : new ArrayList<>(p.getExtraFields()))
                    .setStripSuffix(p.getStripSuffix())
                    .setRules(copyRules(p.getRules())));
        }
        dto.setProfiles(views);
        return dto;
    }

    static HostPrivacyProfiles mergeKeys(HostPrivacyProfiles incoming, HostPrivacyProfiles existing) {
        HostPrivacyProfiles out = new HostPrivacyProfiles();
        List<HostPrivacyProfile> list = incoming == null || incoming.getProfiles() == null
                ? new ArrayList<>() : incoming.getProfiles();
        Map<String, String> oldKeys = new LinkedHashMap<>();
        if (existing != null && existing.getProfiles() != null) {
            for (HostPrivacyProfile p : existing.getProfiles()) {
                if (p != null && StrUtil.isNotBlank(p.getId()) && StrUtil.isNotBlank(p.getDecryptKey())) {
                    oldKeys.put(p.getId(), p.getDecryptKey());
                }
            }
        }
        List<HostPrivacyProfile> merged = new ArrayList<>();
        for (HostPrivacyProfile p : list) {
            if (p == null) {
                continue;
            }
            if (isPlaceholderKey(p.getDecryptKey()) && StrUtil.isNotBlank(p.getId())) {
                p.setDecryptKey(oldKeys.get(p.getId()));
            }
            merged.add(p);
        }
        out.setProfiles(merged);
        return out;
    }

    static void normalizeAndValidate(HostPrivacyProfiles profiles) {
        if (profiles.getProfiles() == null) {
            profiles.setProfiles(new ArrayList<>());
        }
        Set<String> ids = new LinkedHashSet<>();
        int i = 0;
        for (HostPrivacyProfile p : profiles.getProfiles()) {
            i++;
            if (StrUtil.isBlank(p.getName())) {
                throw new FlowException("HOST_PRIVACY_PROFILES_INVALID", "第 " + i + " 套方案缺少名称");
            }
            if (StrUtil.isBlank(p.getId())
                    || PrivacyDecryptAlg.BUILTIN_PROFILE_ID.equalsIgnoreCase(p.getId().trim())) {
                p.setId("p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            } else {
                p.setId(p.getId().trim());
            }
            if (!ids.add(p.getId())) {
                throw new FlowException("HOST_PRIVACY_PROFILES_INVALID", "方案 ID 重复: " + p.getId());
            }
            PrivacyDecryptSpec spec = PrivacyDecryptSpec.fromProfile(p);
            String invalid = spec.validateMessage();
            if (invalid != null) {
                throw new FlowException("HOST_PRIVACY_PROFILES_INVALID",
                        "方案「" + p.getName() + "」" + invalid);
            }
            spec.applyTo(p);
            if (StrUtil.isNotBlank(p.getDecryptKey()) && !spec.isPlain()) {
                byte[] key = PrivacyCryptoService.parseAtRestKey(p.getDecryptKey(), spec.getFamily());
                if (key == null) {
                    throw new FlowException("HOST_PRIVACY_PROFILE_KEY_INVALID",
                            spec.isAes()
                                    ? "方案「" + p.getName() + "」密钥须为 16/24/32 字节明文或对应长度 hex"
                                    : "方案「" + p.getName() + "」密钥须为 16 字节明文或 32 位 hex");
                }
            }
            if (p.getExtraFields() == null) {
                p.setExtraFields(new ArrayList<>());
            }
            if (p.getRules() == null) {
                p.setRules(new ArrayList<>());
            }
            for (PrivacyMaskRule rule : p.getRules()) {
                if (rule == null) {
                    continue;
                }
                rule.setMethod(normalizeMethod(rule.getMethod()));
                rule.setMatchMode(normalizeMatchMode(rule.getMatchMode()));
                if (PrivacyMaskRule.MATCH_REGEX.equals(rule.getMatchMode())
                        && rule.getAliases() != null) {
                    for (String alias : rule.getAliases()) {
                        if (StrUtil.isBlank(alias)) {
                            continue;
                        }
                        if (PrivacyMasker.compileFieldRegex(alias) == null) {
                            throw new FlowException("HOST_PRIVACY_PROFILES_INVALID",
                                    "方案「" + p.getName() + "」脱敏正则无效: " + alias.trim());
                        }
                    }
                }
            }
        }
    }

    static boolean isPlaceholderKey(String key) {
        if (StrUtil.isBlank(key)) {
            return true;
        }
        String t = key.trim();
        String stripped = t.replace("*", "").replace("•", "").replace("·", "").replace(" ", "");
        return stripped.isEmpty();
    }

    static String normalizeMethod(String method) {
        if (StrUtil.isBlank(method)) {
            return PrivacyMaskRule.FULL;
        }
        String t = method.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (t) {
            case "KEEP_HEAD_TAIL", "HEAD_TAIL" -> PrivacyMaskRule.KEEP_HEAD_TAIL;
            case "KEEP_HEAD", "HEAD" -> PrivacyMaskRule.KEEP_HEAD;
            case "KEEP_TAIL", "TAIL" -> PrivacyMaskRule.KEEP_TAIL;
            case "NAME_KEEP_ENDS", "NAME" -> PrivacyMaskRule.NAME_KEEP_ENDS;
            case "PHONE" -> PrivacyMaskRule.PHONE;
            case "ID_CARD", "IDCARD" -> PrivacyMaskRule.ID_CARD;
            case "FULL", "MASK_FULL" -> PrivacyMaskRule.FULL;
            default -> t;
        };
    }

    static String normalizeMatchMode(String matchMode) {
        if (StrUtil.isBlank(matchMode)) {
            return PrivacyMaskRule.MATCH_EXACT;
        }
        String t = matchMode.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "CONTAINS", "CONTAIN", "INCLUDES", "INCLUDE", "SUBSTRING" -> PrivacyMaskRule.MATCH_CONTAINS;
            case "REGEX", "REGEXP", "PATTERN" -> PrivacyMaskRule.MATCH_REGEX;
            default -> PrivacyMaskRule.MATCH_EXACT;
        };
    }

    public static HostPrivacyProfiles parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new HostPrivacyProfiles();
        }
        try {
            HostPrivacyProfiles parsed = MAPPER.readValue(raw, HostPrivacyProfiles.class);
            return parsed != null ? parsed : new HostPrivacyProfiles();
        } catch (Exception e) {
            log.warn("[HostPrivacy] 解析 HOST_PRIVACY_PROFILES 失败，回退空列表: {}", e.getMessage());
            return new HostPrivacyProfiles();
        }
    }

    private static List<PrivacyMaskRule> copyRules(List<PrivacyMaskRule> src) {
        if (src == null) {
            return new ArrayList<>();
        }
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
}
