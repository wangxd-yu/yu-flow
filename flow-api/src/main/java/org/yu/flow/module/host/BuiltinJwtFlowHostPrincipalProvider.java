package org.yu.flow.module.host;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.module.rbac.dto.AuthMeDTO;
import org.yu.flow.module.rbac.service.RbacService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 内置 JWT 主体解析（独立部署兜底）：{@code userType=ADMIN}，角色/权限来自 JWT 与 Flow RBAC。
 */
public class BuiltinJwtFlowHostPrincipalProvider implements FlowHostPrincipalProvider, FlowHostBuiltinSpi {

    private final RbacService rbacService;

    public BuiltinJwtFlowHostPrincipalProvider(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public Optional<FlowHostPrincipal> resolve(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String token = JwtTokenUtil.resolveToken(request);
        if (StrUtil.isBlank(token)) {
            return Optional.empty();
        }
        try {
            JwtTokenUtil.validateToken(token);
        } catch (ValidateException e) {
            return Optional.empty();
        }
        String username = JwtTokenUtil.getUsername(token);
        if (StrUtil.isBlank(username)) {
            return Optional.empty();
        }
        String userId = JwtTokenUtil.getUserId(token);
        if (StrUtil.isBlank(userId)) {
            userId = username;
        }
        Set<String> roles = new HashSet<>(JwtTokenUtil.getRoles(token));
        Set<String> permissions = new HashSet<>();
        if (rbacService != null && rbacService.isRbacEnabled()) {
            try {
                AuthMeDTO me = rbacService.buildMe(username);
                if (me != null) {
                    if (me.getRoles() != null) {
                        roles.addAll(me.getRoles());
                    }
                    if (me.getPermissions() != null) {
                        permissions.addAll(me.getPermissions());
                    }
                }
            } catch (Exception ignored) {
                // 主体仍可用；权限留空
            }
        }
        return Optional.of(FlowHostPrincipal.builder()
                .userId(userId)
                .username(username)
                .userType(FlowHostPrincipal.TYPE_ADMIN)
                .roles(Collections.unmodifiableSet(roles))
                .permissions(Collections.unmodifiableSet(permissions))
                .authChannel("FLOW_JWT")
                .attributes(Collections.emptyMap())
                .build());
    }
}
