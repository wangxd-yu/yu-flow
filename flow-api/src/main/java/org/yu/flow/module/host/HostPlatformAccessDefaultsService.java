package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.privacy.PrivacyDecryptAlg;
import org.yu.flow.module.host.dto.HostPlatformAccessDefaultsDTO;
import org.yu.flow.module.sysconfig.service.SysConfigService;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 接口管理「平台默认」：基础防护走 INGRESS_DEFAULT_*，谁可以调用 / 谁看什么走宿主主体设置。
 */
@Service
public class HostPlatformAccessDefaultsService {

    private static final Set<String> AUTH_MODES = Set.of("NONE", "HOST", "OPEN");

    @Resource
    private YuFlowRuntimeSettings runtimeSettings;
    @Resource
    private SysConfigService sysConfigService;
    @Resource
    private HostPrincipalSettingsStore principalSettingsStore;
    @Resource
    private HostPrivacyProfilesStore privacyProfilesStore;

    public HostPlatformAccessDefaultsDTO load() {
        HostPrincipalSettings principal = principalSettingsStore.load();
        return new HostPlatformAccessDefaultsDTO()
                .setAuthMode(runtimeSettings.getIngressDefaultAuthMode())
                .setRateLimitEnabled(runtimeSettings.isIngressDefaultRateLimitEnabled())
                .setRateLimitQps(runtimeSettings.getIngressDefaultRateLimitQps())
                .setIpAllowlist(runtimeSettings.getIngressDefaultIpAllowlist())
                .setTimeoutMs(runtimeSettings.getIngressDefaultTimeoutMs())
                .setIngressCallerEnabled(principal.isIngressCallerEnabled())
                .setIngressRules(copyCaller(principal.getIngressRules()))
                .setPrivacyRules(copyPrivacy(principal.resolvedPrivacyRules()))
                .setPrivacyProfileId(resolvedProfileId(principal.getPrivacyProfileId()))
                .setPrivacyFieldSuffix(StrUtil.nullToEmpty(principal.getPrivacyFieldSuffix()))
                .setPrivacyExtraFields(copyStrings(principal.getPrivacyExtraFields()))
                .setPrivacyStripSuffix(principal.getPrivacyStripSuffix() == null
                        || principal.getPrivacyStripSuffix());
    }

    @Transactional(rollbackFor = Exception.class)
    public HostPlatformAccessDefaultsDTO save(HostPlatformAccessDefaultsDTO body) {
        HostPlatformAccessDefaultsDTO in = body != null ? body : new HostPlatformAccessDefaultsDTO();
        String authMode = normalizeAuthMode(in.getAuthMode());
        boolean callerOn = Boolean.TRUE.equals(in.getIngressCallerEnabled());
        if ("NONE".equals(authMode) && callerOn) {
            throw new FlowException("PLATFORM_ACCESS_NONE_CALLER",
                    "匿名 (NONE) 时不能启用调用方策略");
        }
        String profileId = normalizeProfileId(in.getPrivacyProfileId());
        assertProfileExists(profileId);

        sysConfigService.updateValueByKey(YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_AUTH_MODE, authMode);
        sysConfigService.updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_RATE_LIMIT_ENABLED,
                boolStr(in.getRateLimitEnabled()));
        sysConfigService.updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_RATE_LIMIT_QPS,
                intStr(in.getRateLimitQps(), runtimeSettings.getIngressDefaultRateLimitQps()));
        sysConfigService.updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_IP_ALLOWLIST,
                in.getIpAllowlist() == null ? "" : in.getIpAllowlist());
        sysConfigService.updateValueByKey(
                YuFlowRuntimeSettings.Keys.INGRESS_DEFAULT_TIMEOUT_MS,
                intStr(in.getTimeoutMs(), runtimeSettings.getIngressDefaultTimeoutMs()));

        HostPrincipalSettings next = principalSettingsStore.load();
        next.setIngressCallerEnabled(callerOn);
        next.setIngressRules(copyCaller(in.getIngressRules()));
        next.setPrivacyRules(copyPrivacy(in.getPrivacyRules()));
        next.setPrivacyProfileId(profileId);
        next.setPrivacyFieldSuffix(StrUtil.trim(in.getPrivacyFieldSuffix()));
        next.setPrivacyExtraFields(copyStrings(in.getPrivacyExtraFields()));
        next.setPrivacyStripSuffix(in.getPrivacyStripSuffix() == null || in.getPrivacyStripSuffix());
        next.setPrivacyRevealRoles(new ArrayList<>());
        next.setPrivacyMaskRoles(new ArrayList<>());
        principalSettingsStore.save(next);

        int qps = in.getRateLimitQps() != null
                ? in.getRateLimitQps()
                : runtimeSettings.getIngressDefaultRateLimitQps();
        int timeoutMs = in.getTimeoutMs() != null
                ? in.getTimeoutMs()
                : runtimeSettings.getIngressDefaultTimeoutMs();
        return new HostPlatformAccessDefaultsDTO()
                .setAuthMode(authMode)
                .setRateLimitEnabled(Boolean.TRUE.equals(in.getRateLimitEnabled()))
                .setRateLimitQps(qps)
                .setIpAllowlist(in.getIpAllowlist() == null ? "" : in.getIpAllowlist())
                .setTimeoutMs(timeoutMs)
                .setIngressCallerEnabled(callerOn)
                .setIngressRules(copyCaller(next.getIngressRules()))
                .setPrivacyRules(copyPrivacy(next.getPrivacyRules()))
                .setPrivacyProfileId(next.getPrivacyProfileId())
                .setPrivacyFieldSuffix(StrUtil.nullToEmpty(next.getPrivacyFieldSuffix()))
                .setPrivacyExtraFields(copyStrings(next.getPrivacyExtraFields()))
                .setPrivacyStripSuffix(next.getPrivacyStripSuffix());
    }

    private static String normalizeAuthMode(String raw) {
        String mode = StrUtil.trim(raw);
        if (StrUtil.isBlank(mode)) {
            return "NONE";
        }
        String upper = mode.toUpperCase(Locale.ROOT);
        if (!AUTH_MODES.contains(upper)) {
            throw new FlowException("PLATFORM_ACCESS_AUTH_MODE", "鉴权方式仅支持 NONE / HOST / OPEN");
        }
        return upper;
    }

    private static String resolvedProfileId(String raw) {
        String id = StrUtil.trim(raw);
        return StrUtil.isBlank(id) ? PrivacyDecryptAlg.BUILTIN_PROFILE_ID : id;
    }

    private static String normalizeProfileId(String raw) {
        return resolvedProfileId(raw);
    }

    private void assertProfileExists(String profileId) {
        if (PrivacyDecryptAlg.BUILTIN_PROFILE_ID.equalsIgnoreCase(profileId)) {
            return;
        }
        if (privacyProfilesStore == null || privacyProfilesStore.find(profileId) == null) {
            throw new FlowException("PLATFORM_ACCESS_PRIVACY_PROFILE",
                    "解密/脱敏方案不存在: " + profileId);
        }
    }

    private static String boolStr(Boolean v) {
        return Boolean.TRUE.equals(v) ? "true" : "false";
    }

    private static String intStr(Integer v, int fallback) {
        return String.valueOf(v != null ? v : fallback);
    }

    private static List<CallerAccessRule> copyCaller(List<CallerAccessRule> rules) {
        return rules == null ? new ArrayList<>() : new ArrayList<>(rules);
    }

    private static List<PrivacyAccessRule> copyPrivacy(List<PrivacyAccessRule> rules) {
        return rules == null ? new ArrayList<>() : new ArrayList<>(rules);
    }

    private static List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (StrUtil.isNotBlank(v)) {
                out.add(v.trim());
            }
        }
        return out;
    }
}
