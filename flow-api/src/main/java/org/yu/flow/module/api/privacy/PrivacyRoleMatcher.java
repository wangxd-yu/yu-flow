package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.HostPrincipalSettings;
import org.yu.flow.module.host.HostPrincipalSettingsStore;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * 按宿主机配置的角色名判定 MASK / REVEAL。角色码任意，不写死。
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
        if (principal == null) {
            return PrivacyClass.MASK;
        }
        if (isOpenApp(principal)) {
            return PrivacyClass.MASK;
        }
        HostPrincipalSettings settings = hostPrincipalSettingsStore == null
                ? null : hostPrincipalSettingsStore.load();
        if (matchesAnyRole(principal.getRoles(), settings == null ? null : settings.getPrivacyRevealRoles())) {
            return PrivacyClass.REVEAL;
        }
        if (isFlowConsole(principal) && hasRevealPerm(principal)) {
            return PrivacyClass.REVEAL;
        }
        return PrivacyClass.MASK;
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

    static boolean matchesAnyRole(Collection<String> actual, Collection<String> configured) {
        if (actual == null || actual.isEmpty() || configured == null || configured.isEmpty()) {
            return false;
        }
        Set<String> have = actual.stream()
                .filter(StrUtil::isNotBlank)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (String role : configured) {
            if (StrUtil.isNotBlank(role) && have.contains(role.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFlowConsole(FlowHostPrincipal principal) {
        return principal != null && CHANNEL_FLOW_JWT.equals(principal.getAuthChannel());
    }

    private static boolean isOpenApp(FlowHostPrincipal principal) {
        if (principal == null) {
            return false;
        }
        if (FlowHostPrincipal.TYPE_OPEN_APP.equalsIgnoreCase(StrUtil.trim(principal.getUserType()))) {
            return true;
        }
        return StrUtil.isNotBlank(principal.getUserId()) && principal.getUserId().startsWith("open:");
    }
}
