package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.host.HostPrincipalSettings;
import org.yu.flow.module.host.HostPrincipalSettingsStore;
import org.yu.flow.module.host.HostPrivacyProfile;
import org.yu.flow.module.host.HostPrivacyProfilesStore;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;

/**
 * 合并接口 privacyConfig → 目录链 → 宿主机方案 → 系统默认。
 */
@Slf4j
@Component
public class PrivacyConfigResolver {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private HostPrivacyProfilesStore hostPrivacyProfilesStore;

    @Resource
    private HostPrincipalSettingsStore hostPrincipalSettingsStore;

    public EffectivePrivacy resolve(FlowApiDO api) {
        return resolve(api, false);
    }

    /**
     * @param useDraft true 时读接口草稿列（管理端预览）；已发布网关走快照
     */
    public EffectivePrivacy resolve(FlowApiDO api, boolean useDraft) {
        ApiPrivacyConfig merged = PrivacyConfigMerge.empty();
        ApiPrivacyConfig apiCfg = parse(resolveApiJson(api, useDraft));
        boolean stop = PrivacyConfigMerge.overlay(merged, apiCfg);
        if (!stop) {
            ApiPrivacyConfig dirCfg = flowDirectoryService != null
                    ? flowDirectoryService.resolveDirectoryPrivacyOverrides(
                    api == null ? null : api.getDirectoryId())
                    : null;
            PrivacyConfigMerge.overlay(merged, dirCfg);
        }
        // 平台默认填剩余空项（含 inherit=false 的接口未写的字段）
        PrivacyConfigMerge.overlay(merged, hostPrivacyLayer());
        return PrivacyConfigMerge.toEffective(merged, PrivacyConfigMerge.systemDefaults(), resolveProfile(merged));
    }

    /** 目录/接口未覆盖时的平台默认：解密方案 + 密文列识别 + 谁看什么。 */
    private ApiPrivacyConfig hostPrivacyLayer() {
        if (hostPrincipalSettingsStore == null) {
            return null;
        }
        HostPrincipalSettings principal = hostPrincipalSettingsStore.load();
        if (principal == null) {
            return null;
        }
        ApiPrivacyConfig layer = new ApiPrivacyConfig();
        if (StrUtil.isNotBlank(principal.getPrivacyProfileId())) {
            layer.setProfileId(principal.getPrivacyProfileId().trim());
        }
        if (StrUtil.isNotBlank(principal.getPrivacyFieldSuffix())) {
            layer.setFieldSuffix(principal.getPrivacyFieldSuffix().trim());
        }
        if (principal.getPrivacyExtraFields() != null && !principal.getPrivacyExtraFields().isEmpty()) {
            layer.setExtraFields(new java.util.ArrayList<>(principal.getPrivacyExtraFields()));
        }
        if (principal.getPrivacyStripSuffix() != null) {
            layer.setStripSuffix(principal.getPrivacyStripSuffix());
        }
        layer.setRules(new java.util.ArrayList<>(principal.resolvedPrivacyRules()));
        return layer;
    }

    public static ApiPrivacyConfig parse(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json.trim(), ApiPrivacyConfig.class);
        } catch (Exception e) {
            log.warn("[Privacy] 解析 privacyConfig 失败: {}", e.getMessage());
            return null;
        }
    }

    private HostPrivacyProfile resolveProfile(ApiPrivacyConfig merged) {
        String profileId = merged == null ? null : StrUtil.trim(merged.getProfileId());
        if (StrUtil.isBlank(profileId)
                || PrivacyDecryptAlg.BUILTIN_PROFILE_ID.equalsIgnoreCase(profileId)) {
            return HostPrivacyProfile.builtin();
        }
        HostPrivacyProfile found = hostPrivacyProfilesStore == null
                ? null : hostPrivacyProfilesStore.find(profileId);
        if (found == null) {
            log.warn("[Privacy] 隐私方案不存在: {}，解密失败关闭", profileId);
            return HostPrivacyProfile.missing(profileId);
        }
        return found;
    }

    private static String resolveApiJson(FlowApiDO api, boolean useDraft) {
        if (api == null) {
            return null;
        }
        if (useDraft) {
            return api.getPrivacyConfig();
        }
        String fromSnap = PublishedApiSnapshot.resolvePrivacyConfig(api);
        return fromSnap != null ? fromSnap : api.getPrivacyConfig();
    }
}
