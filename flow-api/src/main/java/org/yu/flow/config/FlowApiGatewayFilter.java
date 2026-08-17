package org.yu.flow.config;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.config.response.ResponseStrategyResolver;
import org.yu.flow.config.response.ResponseTransformer;
import org.yu.flow.dto.R;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.security.IngressException;
import org.yu.flow.module.api.security.IngressSecurityGuard;
import org.yu.flow.module.api.privacy.PrivacyFieldInterceptor;
import org.yu.flow.module.api.security.IngressSecurityResolver;
import org.yu.flow.module.api.security.IngressAuthMode;
import org.yu.flow.module.api.security.EffectiveSecurity;
import org.yu.flow.module.api.service.ApiDataViewService;
import org.yu.flow.module.api.support.ApiExportPathSupport;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsKeys;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthContext;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.module.rbac.service.RbacService;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.util.FlowObjectMapperUtil;
import org.yu.flow.util.ThrowableUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 动态 API 请求网关核心过滤器
 *
 * <p>基于 Servlet Filter 实现的网关（低侵入设计）：
 * <ol>
 *   <li>管理端（{@code /flow-api}）JWT 鉴权</li>
 *   <li>通过 {@link FlowApiCacheManager} 在纯内存中匹配动态路由</li>
 *   <li>提取请求参数并委托 {@link FlowApiExecutionService#executeApi} 执行业务</li>
 *   <li>写出响应并短路后续的 FilterChain</li>
 * </ol>
 * 不匹配的请求会原样放行给宿主系统。通过设置较低优先级，确保拿得到如 SpringSecurity 等的上下文。
 * </p>
 *
 * <p>本类仅保留请求编排骨架，具体职责拆分至同包协作件：
 * {@link GatewayIo}（I/O 与参数提取）、{@link GatewayManagementAuthHandler}（管理端 JWT / 宿主鉴权）、
 * {@link GatewayWrapForwardHandler}（WRAP 受控转发）、{@link GatewayApiExecutionHandler}（API 执行）、
 * {@link GatewayOpenEntryHandler}（第三方开放入口）。</p>
 *
 * @author yu-flow
 */
@Slf4j
public class FlowApiGatewayFilter extends OncePerRequestFilter {

    private static final String SECURITY_LEVEL_HEADER = "ss-level";
    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    private final YuFlowProperties flowProperties;
    private final FlowApiCacheManager flowApiCacheManager;
    private final AssetMetricsRecorder assetMetricsRecorder;
    private final IngressSecurityResolver ingressSecurityResolver;
    private final IngressSecurityGuard ingressSecurityGuard;
    private final YuFlowRuntimeSettings yuFlowRuntimeSettings;

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();
    private final UrlPathHelper urlPathHelper = createUrlPathHelper();

    private final GatewayIo io;
    private final GatewayManagementAuthHandler authHandler;
    private final GatewayWrapForwardHandler wrapForwardHandler;
    private final GatewayApiExecutionHandler executionHandler;
    private final GatewayOpenEntryHandler openEntryHandler;

    public FlowApiGatewayFilter(YuFlowProperties flowProperties,
                                FlowApiExecutionService flowApiService,
                                FlowApiCacheManager flowApiCacheManager,
                                SchemaValidatorService schemaValidatorService,
                                ContractParamTypeConverter contractParamTypeConverter,
                                ResponseStrategyResolver responseStrategyResolver,
                                ResponseTransformer responseTransformer,
                                ApiResponseCacheService apiResponseCacheService,
                                OpenAuthService openAuthService,
                                AssetMetricsRecorder assetMetricsRecorder,
                                HostAuthenticationProbe hostAuthenticationProbe,
                                IngressSecurityResolver ingressSecurityResolver,
                                IngressSecurityGuard ingressSecurityGuard,
                                YuFlowRuntimeSettings yuFlowRuntimeSettings,
                                ApiDataViewService apiDataViewService,
                                RbacService rbacService) {
        this(flowProperties, flowApiService, flowApiCacheManager, schemaValidatorService,
                contractParamTypeConverter, responseStrategyResolver, responseTransformer,
                apiResponseCacheService, openAuthService, assetMetricsRecorder, hostAuthenticationProbe,
                ingressSecurityResolver, ingressSecurityGuard, yuFlowRuntimeSettings,
                apiDataViewService, rbacService, null, null);
    }

    public FlowApiGatewayFilter(YuFlowProperties flowProperties,
                                FlowApiExecutionService flowApiService,
                                FlowApiCacheManager flowApiCacheManager,
                                SchemaValidatorService schemaValidatorService,
                                ContractParamTypeConverter contractParamTypeConverter,
                                ResponseStrategyResolver responseStrategyResolver,
                                ResponseTransformer responseTransformer,
                                ApiResponseCacheService apiResponseCacheService,
                                OpenAuthService openAuthService,
                                AssetMetricsRecorder assetMetricsRecorder,
                                HostAuthenticationProbe hostAuthenticationProbe,
                                IngressSecurityResolver ingressSecurityResolver,
                                IngressSecurityGuard ingressSecurityGuard,
                                YuFlowRuntimeSettings yuFlowRuntimeSettings,
                                ApiDataViewService apiDataViewService,
                                RbacService rbacService,
                                org.yu.flow.log.execution.service.FlowExecutionLogService flowExecutionLogService) {
        this(flowProperties, flowApiService, flowApiCacheManager, schemaValidatorService,
                contractParamTypeConverter, responseStrategyResolver, responseTransformer,
                apiResponseCacheService, openAuthService, assetMetricsRecorder, hostAuthenticationProbe,
                ingressSecurityResolver, ingressSecurityGuard, yuFlowRuntimeSettings,
                apiDataViewService, rbacService, flowExecutionLogService, null);
    }

    public FlowApiGatewayFilter(YuFlowProperties flowProperties,
                                FlowApiExecutionService flowApiService,
                                FlowApiCacheManager flowApiCacheManager,
                                SchemaValidatorService schemaValidatorService,
                                ContractParamTypeConverter contractParamTypeConverter,
                                ResponseStrategyResolver responseStrategyResolver,
                                ResponseTransformer responseTransformer,
                                ApiResponseCacheService apiResponseCacheService,
                                OpenAuthService openAuthService,
                                AssetMetricsRecorder assetMetricsRecorder,
                                HostAuthenticationProbe hostAuthenticationProbe,
                                IngressSecurityResolver ingressSecurityResolver,
                                IngressSecurityGuard ingressSecurityGuard,
                                YuFlowRuntimeSettings yuFlowRuntimeSettings,
                                ApiDataViewService apiDataViewService,
                                RbacService rbacService,
                                org.yu.flow.log.execution.service.FlowExecutionLogService flowExecutionLogService,
                                PrivacyFieldInterceptor privacyFieldInterceptor) {
        this.flowProperties = flowProperties;
        this.flowApiCacheManager = flowApiCacheManager;
        this.assetMetricsRecorder = assetMetricsRecorder;
        this.ingressSecurityResolver = ingressSecurityResolver;
        this.ingressSecurityGuard = ingressSecurityGuard;
        this.yuFlowRuntimeSettings = yuFlowRuntimeSettings;

        this.io = new GatewayIo(objectMapper);
        this.authHandler = new GatewayManagementAuthHandler(flowProperties, yuFlowRuntimeSettings,
                rbacService, hostAuthenticationProbe, io, urlPathHelper);
        this.wrapForwardHandler = new GatewayWrapForwardHandler(assetMetricsRecorder,
                flowExecutionLogService, urlPathHelper);
        this.executionHandler = new GatewayApiExecutionHandler(flowApiService, schemaValidatorService,
                contractParamTypeConverter, responseStrategyResolver, responseTransformer,
                apiResponseCacheService, assetMetricsRecorder, apiDataViewService,
                ingressSecurityResolver, privacyFieldInterceptor, io, objectMapper);
        this.openEntryHandler = new GatewayOpenEntryHandler(flowProperties, yuFlowRuntimeSettings,
                openAuthService, flowApiCacheManager, assetMetricsRecorder,
                executionHandler, wrapForwardHandler, io);
    }

    private UrlPathHelper createUrlPathHelper() {
        UrlPathHelper helper = new UrlPathHelper();
        helper.setAlwaysUseFullPath(false);
        helper.setUrlDecode(false);
        return helper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 全局开关关闭，直接放行
        if (!flowProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // WRAP 受控转发防重入：已标记则不再二次匹配/执行
        if (Boolean.TRUE.equals(request.getAttribute(GatewayWrapForwardHandler.ATTR_HOST_WRAP_FORWARDED))) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestPath = urlPathHelper.getPathWithinApplication(request);
        if (requestPath == null) {
            requestPath = "";
        }

        // 2. 静态页 / SPA：按应用内路径或原始 URI 识别，避免 context-path 未剥离时误入动态路由（会变成 500）
        if (isFlowUiRequest(request, requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2.1 短期 Excel 下载链：放行给 Spring MVC（不强制管理端 JWT）
        if (requestPath.startsWith("/flow-api/download/excel/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestMethod = request.getMethod();

        // 2.5 第三方开放入口：/flow-api/open/{真实path}（不走管理端 JWT）
        if (openEntryHandler.isOpenEntryPath(requestPath)) {
            String realPath = openEntryHandler.extractRealPath(requestPath);
            if (openEntryHandler.isNativeOpenOssPath(realPath)) {
                openEntryHandler.authenticateAndPassToMvc(
                        request, response, filterChain, realPath, requestMethod);
            } else {
                openEntryHandler.handleOpenEntry(request, response, filterChain, requestPath, requestMethod);
            }
            return;
        }

        // 3. 管理端鉴权（含 /flow-api/v3/api-docs* OpenAPI 契约）
        //    双层：① Flow JWT + RBAC；② 可选 HostAuthenticationProbe（嵌入时读宿主 Session）
        //    login / OSS 原生接口跳过本段：OSS 由场景访问规则 + @RequirePerm 在 MVC 内控制
        if (requestPath.startsWith("/flow-api")) {
            if (isFlowManagementAuthExempt(requestPath)) {
                filterChain.doFilter(request, response);
                return;
            }
            if (!authHandler.assertManagementJwtOnPrefix(request, response)) {
                return;
            }
            if (!authHandler.assertManagementHostAuthIfRequired(request, response)) {
                return;
            }
        }

        try {
            String matchPath = requestPath;
            boolean excelExport = false;
            String businessPath = ApiExportPathSupport.stripExportSuffix(requestPath);
            if (businessPath != null) {
                excelExport = true;
                matchPath = businessPath;
            }

            // 4. 路由匹配（纯内存，零网络 I/O）
            FlowApiDO flowApiDO = flowApiCacheManager.getExactMatch(requestMethod, matchPath);

            // 精确未命中 → Ant 模式匹配 O(N)
            if (flowApiDO == null) {
                FlowApiCacheManager.AntMatchResult matchResult =
                        flowApiCacheManager.getPatternMatch(requestMethod, matchPath, ANT_PATH_MATCHER);
                if (matchResult != null) {
                    flowApiDO = matchResult.getApi();
                    if (matchResult.getPathVariables() != null && !matchResult.getPathVariables().isEmpty()) {
                        request.setAttribute("flowPathVariables", matchResult.getPathVariables());
                    }
                }
            }

            // 未匹配到动态路由（可能是宿主系统接口），放行
            if (flowApiDO == null) {
                // 带 /export 但未命中业务 API → 放行给宿主/Spring MVC
                // （如管理端 /flow-api/api/{id}/data/export、宿主自有 export 接口）；
                // 路径确实不存在时由宿主自行 404
                filterChain.doFilter(request, response);
                return;
            }

            // 4.4 可选：真实 path + AppKey → 开放鉴权（须在 WRAP 之前，以便开放入口同样可纳管 WRAP）
            if (openEntryHandler.shouldHandleDirectOpen(request)) {
                openEntryHandler.handleOpenOnRealPath(
                        request, response, filterChain, requestPath, requestMethod, flowApiDO);
                return;
            }

            // 4.5 同名包裹（WRAP）：信任宿主鉴权（ingress 关时不强制管理 JWT），转发宿主后写观测
            if (PublishedApiSnapshot.isWrap(flowApiDO)) {
                if (excelExport) {
                    io.writeJsonResponse(response, HttpStatus.BAD_REQUEST.value(),
                            R.fail(400, "宿主包裹接口不支持 /export 导出"));
                    return;
                }
                HttpServletRequest wrapRequest = request;
                if (isIngressEnabled()) {
                    try {
                        EffectiveSecurity sec = ingressSecurityResolver.resolve(flowApiDO);
                        if (sec.getAuthMode() == IngressAuthMode.OPEN) {
                            wrapRequest = openEntryHandler.wrapOpenBody(request);
                        }
                        OpenAuthContext ingressCtx = ingressSecurityGuard.enforce(
                                wrapRequest, flowApiDO, requestPath, requestMethod);
                        if (ingressCtx != null) {
                            wrapRequest.setAttribute("yuOpenPlatformId", ingressCtx.getPlatformId());
                            wrapRequest.setAttribute("yuOpenAppKey", ingressCtx.getAppKey());
                        }
                    } catch (IngressException e) {
                        io.writeJsonResponse(response, e.getHttpStatus(),
                                R.failWithErrorCode(e.getHttpStatus(), e.getCode(), e.getMessage()));
                        return;
                    }
                } else if (!authHandler.assertHostAuthIfRequired(request, response)) {
                    return;
                }
                String wrapMethod = PublishedApiSnapshot.resolveMethod(flowApiDO);
                if (StrUtil.isNotBlank(wrapMethod) && !requestMethod.equalsIgnoreCase(wrapMethod)) {
                    io.writeJsonResponse(response, HttpStatus.METHOD_NOT_ALLOWED.value(),
                            R.fail(HttpStatus.METHOD_NOT_ALLOWED.value(),
                                    String.format("此接口不支持 %s 请求。请改用 %s 请求。", requestMethod, wrapMethod)));
                    return;
                }
                wrapForwardHandler.wrapForwardToHost(wrapRequest, response, filterChain, flowApiDO);
                return;
            }

            // 4.6 入站防护（全局+按接口）；REPLACE 在关闭时仍强制管理端 JWT（禁止动态 API 匿名裸奔）
            HttpServletRequest effectiveRequest = request;
            if (isIngressEnabled()) {
                try {
                    EffectiveSecurity sec = ingressSecurityResolver.resolve(flowApiDO);
                    if (sec.getAuthMode() == IngressAuthMode.OPEN) {
                        effectiveRequest = openEntryHandler.wrapOpenBody(request);
                    }
                    // 鉴权 path 使用原始请求 path（含 /export）
                    OpenAuthContext ingressCtx = ingressSecurityGuard.enforce(
                            effectiveRequest, flowApiDO, requestPath, requestMethod);
                    if (ingressCtx != null) {
                        effectiveRequest.setAttribute("yuOpenPlatformId", ingressCtx.getPlatformId());
                        effectiveRequest.setAttribute("yuOpenAppKey", ingressCtx.getAppKey());
                    }
                } catch (IngressException e) {
                    io.writeJsonResponse(response, e.getHttpStatus(),
                            R.failWithErrorCode(e.getHttpStatus(), e.getCode(), e.getMessage()));
                    return;
                }
            } else if (!authHandler.assertManagementJwt(request, response)) {
                return;
            } else if (!authHandler.assertHostAuthIfRequired(request, response)) {
                return;
            }

            // 5. 安全级别旁路判断
            if (shouldBypassBySecurityLevel(effectiveRequest, flowApiDO)) {
                filterChain.doFilter(effectiveRequest, response);
                return;
            }

            // 6. 请求方法校验
            String configMethod = flowApiDO.getMethod();
            if (!requestMethod.equalsIgnoreCase(configMethod)) {
                io.writeJsonResponse(response, HttpStatus.METHOD_NOT_ALLOWED.value(),
                        R.fail(HttpStatus.METHOD_NOT_ALLOWED.value(),
                                String.format("此接口不支持 %s 请求。请改用 %s 请求。", requestMethod, configMethod)));
                return;
            }

            // 7. 执行业务逻辑（Excel 导出或 JSON）
            try {
                if (excelExport) {
                    executionHandler.executeExcelExport(effectiveRequest, response, flowApiDO);
                } else {
                    executionHandler.executeAndWriteResponse(effectiveRequest, response, flowApiDO);
                }
            } catch (Exception e) {
                log.error("[FlowApiGatewayFilter] API 业务执行异常:\n{}", ThrowableUtil.getStackTrace(e));
                if (excelExport) {
                    executionHandler.handleExcelExportException(response, e);
                } else {
                    executionHandler.handleExceptionResponse(response, flowApiDO, e);
                }
                if (assetMetricsRecorder != null && flowApiDO.getId() != null
                        && request.getAttribute("yuApiMetricsByService") == null) {
                    assetMetricsRecorder.record(MetricsAssetType.API, flowApiDO.getId(),
                            MetricsOutcome.FAIL, 0L,
                            excelExport ? MetricsKeys.TRIGGER_EXPORT : MetricsKeys.TRIGGER_DEFAULT);
                }
            }

            // 执行完毕，直接 return 中断 FilterChain，绝不进入宿主应用逻辑
            return;

        } catch (Exception fatalEx) {
            log.error("[FlowApiGatewayFilter] 网关发生意外错误:", fatalEx);
            io.writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    R.fail(500, "网关内部系统错误：" + fatalEx.getMessage()));
        }
    }

    /**
     * 不走管理端 JWT / 宿主 Probe：登录口与 OSS 原生接口。
     * OSS 细控在 MVC（场景访问规则、{@code @RequirePerm}）。
     */
    static boolean isFlowManagementAuthExempt(String path) {
        return path.startsWith("/flow-api/login")
                || "/flow-api/oss".equals(path)
                || path.startsWith("/flow-api/oss/");
    }

    static boolean isFlowUiRequest(HttpServletRequest request, String pathWithinApp) {
        if (pathWithinApp != null && (pathWithinApp.equals("/flow-ui")
                || pathWithinApp.equals("/flow-ui.html")
                || pathWithinApp.startsWith("/flow-ui/"))) {
            return true;
        }
        String uri = request == null ? null : request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        int q = uri.indexOf('?');
        if (q >= 0) {
            uri = uri.substring(0, q);
        }
        return uri.contains("/flow-ui/") || uri.endsWith("/flow-ui");
    }

    private boolean isIngressEnabled() {
        boolean enabled = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.isIngressEnabled()
                : flowProperties.getIngress() != null && flowProperties.getIngress().isEnabled();
        return enabled && ingressSecurityResolver != null && ingressSecurityGuard != null;
    }

    private static boolean shouldBypassBySecurityLevel(HttpServletRequest request, FlowApiDO flowApiDO) {
        String ssLevelHeader = request.getHeader(SECURITY_LEVEL_HEADER);
        if (ssLevelHeader == null) {
            return false;
        }
        try {
            int ssLevel = Integer.parseInt(ssLevelHeader.trim());
            int dbLevel = flowApiDO.getLevel() != null ? flowApiDO.getLevel() : 0;
            return ssLevel > dbLevel;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
