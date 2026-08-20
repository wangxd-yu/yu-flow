package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.HostPrincipalSettings;
import org.yu.flow.module.host.HostPrincipalSettingsStore;
import org.yu.flow.module.host.PrincipalMatchEngine;
import org.yu.flow.module.host.PrivacyAccessRule;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Locale;

/**
 * 按隐私规则从上到下第一条命中判定 MASK / REVEAL；未命中一律 MASK。
 * {@code flow:privacy:reveal} / {@code *} 只给 Flow 管理端预览，不替代宿主规则。
 */
@Component
public class PrivacyRoleMatcher {

    public static final String FLOW_PERM_REVEAL = "flow:privacy:reveal";
    public static final String CHANNEL_FLOW_JWT = "FLOW_JWT";

    @Resource
    private HostPrincipalSettingsStore hostPrincipalSettingsStore;

    @Resource
    private RbacService rbacService;

    public PrivacyClass resolve(FlowHostPrincipal principal) {
        return resolveDecision(principal, null).getPrivacyClass();
    }

    public PrivacyDecision resolveDecision(FlowHostPrincipal principal, List<PrivacyAccessRule> rules) {
        if (principal == null) {
            return PrivacyDecision.mask();
        }
        List<PrivacyAccessRule> effective = rules;
        if (effective == null) {
            HostPrincipalSettings settings = hostPrincipalSettingsStore == null
                    ? null : hostPrincipalSettingsStore.load();
            effective = settings == null ? List.of() : settings.resolvedPrivacyRules();
        }
        if (PrincipalMatchEngine.isOpenApp(principal)) {
            return firstHit(principal, effective, true);
        }
        if (isFlowConsole(principal) && hasRevealPerm(principal)) {
            return PrivacyDecision.of(PrivacyClass.REVEAL);
        }
        return firstHit(principal, effective, false);
    }

    private static PrivacyDecision firstHit(FlowHostPrincipal principal, List<PrivacyAccessRule> rules,
                                            boolean openAppOnly) {
        if (rules == null || rules.isEmpty()) {
            return PrivacyDecision.mask();
        }
        for (PrivacyAccessRule rule : rules) {
            if (rule == null) {
                continue;
            }
            if (openAppOnly
                    && !PrivacyAccessRule.PRINCIPALS_OPEN.equals(
                    PrincipalMatchEngine.normalizePrincipals(rule.getPrincipals()))) {
                continue;
            }
            if (!PrincipalMatchEngine.matches(rule, principal)) {
                continue;
            }
            PrivacyClass cls = PrivacyAccessRule.PRIVACY_REVEAL.equalsIgnoreCase(StrUtil.trim(rule.getPrivacy()))
                    ? PrivacyClass.REVEAL
                    : PrivacyClass.MASK;
            return PrivacyDecision.of(cls, rule.getFields(), rule.getName());
        }
        return PrivacyDecision.mask();
    }

    private boolean hasRevealPerm(FlowHostPrincipal principal) {
        if (rbacService == null || StrUtil.isBlank(principal.getUsername())) {
            return false;
        }
        try {
            return rbacService.hasAnyPerm(principal.getUsername(), FLOW_PERM_REVEAL, "*");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isFlowConsole(FlowHostPrincipal principal) {
        return principal != null && CHANNEL_FLOW_JWT.equals(principal.getAuthChannel());
    }
}
