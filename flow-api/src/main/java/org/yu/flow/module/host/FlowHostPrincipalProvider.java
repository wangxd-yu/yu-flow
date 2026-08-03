package org.yu.flow.module.host;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 解析当前请求主体；empty 表示未登录。
 */
public interface FlowHostPrincipalProvider {

    Optional<FlowHostPrincipal> resolve(HttpServletRequest request);
}
