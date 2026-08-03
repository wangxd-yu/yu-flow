package org.yu.flow.module.host;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.yu.flow.exception.FlowException;

/**
 * 宿主身份与数据范围辅助工具。
 */
@Component
public class FlowHostAuthSupport {

    @Resource
    private FlowHostPrincipalProvider principalProvider;

    @Resource
    private FlowHostDataScopeProvider dataScopeProvider;

    public FlowHostPrincipal requirePrincipal(HttpServletRequest request) {
        return principalProvider.resolve(request)
                .orElseThrow(() -> new FlowException("RBAC_UNAUTHORIZED", "未登录或凭证无效"));
    }

    public FlowHostDataScope resolveScope(HttpServletRequest request, String domain) {
        FlowHostPrincipal principal = principalProvider.resolve(request).orElse(null);
        if (principal == null) {
            return FlowHostDataScope.deny();
        }
        return dataScopeProvider.resolve(request, principal, domain);
    }

    public static boolean isBuiltin(Object provider) {
        return provider instanceof FlowHostBuiltinSpi;
    }

    public FlowHostPrincipalProvider getPrincipalProvider() {
        return principalProvider;
    }

    public FlowHostDataScopeProvider getDataScopeProvider() {
        return dataScopeProvider;
    }
}
