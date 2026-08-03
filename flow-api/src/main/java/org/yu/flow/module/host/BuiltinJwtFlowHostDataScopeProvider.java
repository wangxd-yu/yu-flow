package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.yu.flow.module.rbac.service.RbacService;

/**
 * 内置 JWT 数据范围：flow:oss:admin 或 * → ALL，否则 SELF。
 */
public class BuiltinJwtFlowHostDataScopeProvider implements FlowHostDataScopeProvider, FlowHostBuiltinSpi {

    private final RbacService rbacService;

    public BuiltinJwtFlowHostDataScopeProvider(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public FlowHostDataScope resolve(HttpServletRequest request, FlowHostPrincipal principal, String domain) {
        if (principal == null || StrUtil.isBlank(principal.getUsername())) {
            return FlowHostDataScope.deny();
        }
        if (rbacService.hasAnyPerm(principal.getUsername(), "flow:oss:admin", "*")) {
            return FlowHostDataScope.all();
        }
        return FlowHostDataScope.self();
    }
}
