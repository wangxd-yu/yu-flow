package org.yu.flow.module.host;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.yu.flow.auto.util.JwtTokenUtil;

import java.util.Collections;
import java.util.Optional;

/**
 * 内置 JWT 主体解析（独立部署兜底）。
 */
public class BuiltinJwtFlowHostPrincipalProvider implements FlowHostPrincipalProvider, FlowHostBuiltinSpi {

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
        return Optional.of(FlowHostPrincipal.builder()
                .userId(userId)
                .username(username)
                .roles(Collections.emptySet())
                .attributes(Collections.emptyMap())
                .build());
    }
}
