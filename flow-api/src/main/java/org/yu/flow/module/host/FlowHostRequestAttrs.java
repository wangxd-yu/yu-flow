package org.yu.flow.module.host;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已发布 API 入站后挂到 {@link HttpServletRequest} 的宿主主体属性，并可供执行引擎读取。
 */
public final class FlowHostRequestAttrs {

    public static final String PRINCIPAL = "yuHostPrincipal";
    public static final String USER_ID = "yuHostUserId";
    public static final String USERNAME = "yuHostUsername";
    public static final String USER_TYPE = "yuHostUserType";

    private FlowHostRequestAttrs() {
    }

    public static void bind(HttpServletRequest request, FlowHostPrincipal principal) {
        if (request == null) {
            return;
        }
        if (principal == null) {
            request.removeAttribute(PRINCIPAL);
            request.removeAttribute(USER_ID);
            request.removeAttribute(USERNAME);
            request.removeAttribute(USER_TYPE);
            return;
        }
        request.setAttribute(PRINCIPAL, principal);
        request.setAttribute(USER_ID, principal.getUserId());
        request.setAttribute(USERNAME, principal.getUsername());
        request.setAttribute(USER_TYPE, principal.getUserType());
    }

    public static FlowHostPrincipal getPrincipal(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object v = request.getAttribute(PRINCIPAL);
        return v instanceof FlowHostPrincipal ? (FlowHostPrincipal) v : null;
    }

    /** 供流程 {@code @AUTH} 上下文使用的扁平 Map。 */
    public static Map<String, Object> toAuthContext(FlowHostPrincipal principal) {
        if (principal == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("userId", principal.getUserId());
        auth.put("username", principal.getUsername());
        auth.put("userType", principal.getUserType());
        auth.put("deptId", principal.getDeptId());
        auth.put("deptIds", principal.getDeptIds() == null ? Collections.emptySet() : principal.getDeptIds());
        auth.put("roles", principal.getRoles() == null ? Collections.emptySet() : principal.getRoles());
        auth.put("permissions",
                principal.getPermissions() == null ? Collections.emptySet() : principal.getPermissions());
        auth.put("authChannel", principal.getAuthChannel());
        auth.put("attributes",
                principal.getAttributes() == null ? Collections.emptyMap() : principal.getAttributes());
        return auth;
    }
}
