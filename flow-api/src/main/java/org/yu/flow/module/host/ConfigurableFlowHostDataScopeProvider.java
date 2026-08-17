package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 缺省数据范围：宿主主体按「宿主机配置」的管理员类型判定，管理端 JWT 仍走内置规则。
 *
 * <p>配置里勾为管理员的用户类型 → {@code ALL}（可见全部），其余宿主用户 → {@code SELF}（只见本人）。
 * 需要部门/用户列表范围时仍要实现 {@link FlowHostDataScopeProvider}。</p>
 */
public class ConfigurableFlowHostDataScopeProvider implements FlowHostDataScopeProvider, FlowHostBuiltinSpi {

    private final BuiltinJwtFlowHostDataScopeProvider builtin;
    private final HostPrincipalSettingsStore settingsStore;

    public ConfigurableFlowHostDataScopeProvider(BuiltinJwtFlowHostDataScopeProvider builtin,
                                                 HostPrincipalSettingsStore settingsStore) {
        this.builtin = builtin;
        this.settingsStore = settingsStore;
    }

    @Override
    public FlowHostDataScope resolve(HttpServletRequest request, FlowHostPrincipal principal, String domain) {
        if (principal == null || StrUtil.isBlank(principal.getUserId())) {
            return FlowHostDataScope.deny();
        }
        HostPrincipalSettings settings = settingsStore.load();
        if (!settings.isEnabled() || !isHostPrincipal(principal)) {
            return builtin.resolve(request, principal, domain);
        }
        return settings.isAdminUserType(principal.getUserType())
                ? FlowHostDataScope.all()
                : FlowHostDataScope.self();
    }

    /** 只有配置式解析出来的主体按宿主规则判定，Flow 管理端与开放平台维持原有语义。 */
    private static boolean isHostPrincipal(FlowHostPrincipal principal) {
        String channel = principal.getAuthChannel();
        return HostPrincipalSettings.CHANNEL_API.equals(channel)
                || HostPrincipalSettings.CHANNEL_HEADER.equals(channel);
    }
}
