package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.annotation.Resource;
import java.util.Collection;

/**
 * 保留身份目录 API 虽复用通用 API 编辑器，但写入还必须具备宿主配置管理权限。
 */
@Component
public class HostReservedApiWriteGuard {

    @Resource
    private RbacService rbacService;

    public void assertAllowed(String apiId) {
        if (!HostCatalogReserved.isReservedId(apiId) || !rbacService.isRbacEnabled()) {
            return;
        }
        String username = JwtTokenUtil.currentUsername();
        if (StrUtil.isBlank(username)
                || !rbacService.hasAnyPerm(username, HostCatalogApiBootstrap.PERM_WRITE, "*")) {
            throw new FlowException("RBAC_FORBIDDEN", "修改宿主身份目录需要权限: "
                    + HostCatalogApiBootstrap.PERM_WRITE);
        }
    }

    public void assertAllowed(Collection<String> apiIds) {
        if (apiIds == null) {
            return;
        }
        for (String apiId : apiIds) {
            assertAllowed(apiId);
        }
    }
}
