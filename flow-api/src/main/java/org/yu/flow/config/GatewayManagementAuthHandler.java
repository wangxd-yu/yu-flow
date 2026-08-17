package org.yu.flow.config;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UrlPathHelper;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.dto.R;
import org.yu.flow.dto.ResultCode;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthException;
import org.yu.flow.module.rbac.service.RbacService;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 管理端鉴权协作件：JWT 校验（token 存在 / 有效 / 主体启用）与宿主鉴权断言。
 * <p>从 {@link FlowApiGatewayFilter} 拆出，行为保持一致。</p>
 */
class GatewayManagementAuthHandler {

    private final YuFlowProperties flowProperties;
    private final YuFlowRuntimeSettings yuFlowRuntimeSettings;
    private final RbacService rbacService;
    private final HostAuthenticationProbe hostAuthenticationProbe;
    private final GatewayIo io;
    private final UrlPathHelper urlPathHelper;

    GatewayManagementAuthHandler(YuFlowProperties flowProperties,
                                 YuFlowRuntimeSettings yuFlowRuntimeSettings,
                                 RbacService rbacService,
                                 HostAuthenticationProbe hostAuthenticationProbe,
                                 GatewayIo io,
                                 UrlPathHelper urlPathHelper) {
        this.flowProperties = flowProperties;
        this.yuFlowRuntimeSettings = yuFlowRuntimeSettings;
        this.rbacService = rbacService;
        this.hostAuthenticationProbe = hostAuthenticationProbe;
        this.io = io;
        this.urlPathHelper = urlPathHelper;
    }

    /**
     * /flow-api 前缀的管理端 JWT 三段校验（token 存在 / 有效 / 会话主体启用）。
     *
     * @return false 表示已写出拒绝响应
     */
    boolean assertManagementJwtOnPrefix(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String token = JwtTokenUtil.resolveToken(request);
        if (token == null) {
            io.writeJsonResponse(response, HttpStatus.UNAUTHORIZED.value(),
                    R.fail(ResultCode.TOKEN_EMPTY.getCode(), "token 不能为空！"));
            return false;
        }
        try {
            JwtTokenUtil.validateToken(token);
        } catch (ValidateException e) {
            io.writeJsonResponse(response, HttpStatus.UNAUTHORIZED.value(),
                    R.fail(ResultCode.TOKEN_INVALID.getCode(), "token 已失效！"));
            return false;
        }
        // 会话主体必须存在且启用（拒绝幽灵/禁用用户 JWT）
        String sessionUser = JwtTokenUtil.getUsername(token);
        if (!isActiveManagementPrincipal(sessionUser)) {
            io.writeJsonResponse(response, HttpStatus.UNAUTHORIZED.value(),
                    R.fail(ResultCode.TOKEN_INVALID.getCode(), "用户不存在或已禁用"));
            return false;
        }
        return true;
    }

    /**
     * 已发布动态 API（REPLACE）在未启用 ingress 时的安全默认：必须携带合法管理端 JWT。
     * （路径不一定以 /flow-api 开头，故不能只依赖上文管理端鉴权分支。）
     * <p>WRAP 不走本方法，默认信任宿主鉴权。</p>
     *
     * @return false 表示已写出拒绝响应
     */
    boolean assertManagementJwt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // 已在 /flow-api 前缀分支校验过的请求无需重复
        String path = urlPathHelper.getPathWithinApplication(request);
        if (path != null && path.startsWith("/flow-api")) {
            return true;
        }
        return assertManagementJwtOnPrefix(request, response);
    }

    /**
     * @return false 表示已写出拒绝响应，调用方应直接 return
     */
    boolean assertHostAuthIfRequired(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        boolean require = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.isOpenRequireHostAuth()
                : flowProperties.getOpen() != null && flowProperties.getOpen().isRequireHostAuth();
        if (!require) {
            return true;
        }
        boolean ok = hostAuthenticationProbe == null || hostAuthenticationProbe.isAuthenticated(request);
        if (ok) {
            return true;
        }
        OpenAuthException e = OpenAuthException.hostAuthRequired();
        io.writeJsonResponse(response, e.getHttpStatus(),
                R.failWithErrorCode(e.getHttpStatus(), e.getCode(), e.getMessage()));
        return false;
    }

    /**
     * 管理端 {@code /flow-api/**} 在 JWT 通过后的宿主登录探测（双层鉴权第二关）。
     * <p>login / captcha 不走本方法。独立部署默认 Probe=JWT，与上一关等价。</p>
     *
     * @return false 表示已写出拒绝响应
     */
    boolean assertManagementHostAuthIfRequired(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        boolean require = flowProperties.getSecurity() != null
                && flowProperties.getSecurity().isManagementRequireHostAuth();
        if (!require) {
            return true;
        }
        boolean ok = hostAuthenticationProbe == null || hostAuthenticationProbe.isAuthenticated(request);
        if (ok) {
            return true;
        }
        OpenAuthException e = OpenAuthException.managementHostAuthRequired();
        io.writeJsonResponse(response, e.getHttpStatus(),
                R.failWithErrorCode(e.getHttpStatus(), e.getCode(), e.getMessage()));
        return false;
    }

    /**
     * 管理端会话主体须存在且启用（含受控的 yml 兜底账号）；拒绝幽灵/禁用用户 JWT。
     */
    private boolean isActiveManagementPrincipal(String username) {
        if (StrUtil.isBlank(username) || rbacService == null) {
            return false;
        }
        return rbacService.buildMe(username) != null;
    }
}
