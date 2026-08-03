package org.yu.flow.module.host;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 解析当前请求的数据范围。
 */
public interface FlowHostDataScopeProvider {

    FlowHostDataScope resolve(HttpServletRequest request, FlowHostPrincipal principal, String domain);
}
