package org.yu.flow.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.yu.flow.dto.R;
import org.yu.flow.log.open.domain.FlowOpenCallLogDO;
import org.yu.flow.log.open.support.OpenCallLogRecorder;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.ApiExportPathSupport;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.open.auth.OpenAuthContext;
import org.yu.flow.module.open.auth.OpenAuthException;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.module.open.support.CachedBodyHttpServletRequest;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.util.ThrowableUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 第三方开放入口协作件：/flow-api/open/** 与真实 path + AppKey 直连的开放鉴权、执行与调用日志。
 * <p>从 {@link FlowApiGatewayFilter} 拆出，行为保持一致（日志前缀沿用 [FlowApiGatewayFilter]）。</p>
 */
@Slf4j
class GatewayOpenEntryHandler {

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    private final YuFlowProperties flowProperties;
    private final YuFlowRuntimeSettings yuFlowRuntimeSettings;
    private final OpenAuthService openAuthService;
    private final FlowApiCacheManager flowApiCacheManager;
    private final AssetMetricsRecorder assetMetricsRecorder;
    private final GatewayApiExecutionHandler executionHandler;
    private final GatewayWrapForwardHandler wrapForwardHandler;
    private final GatewayIo io;

    GatewayOpenEntryHandler(YuFlowProperties flowProperties,
                            YuFlowRuntimeSettings yuFlowRuntimeSettings,
                            OpenAuthService openAuthService,
                            FlowApiCacheManager flowApiCacheManager,
                            AssetMetricsRecorder assetMetricsRecorder,
                            GatewayApiExecutionHandler executionHandler,
                            GatewayWrapForwardHandler wrapForwardHandler,
                            GatewayIo io) {
        this.flowProperties = flowProperties;
        this.yuFlowRuntimeSettings = yuFlowRuntimeSettings;
        this.openAuthService = openAuthService;
        this.flowApiCacheManager = flowApiCacheManager;
        this.assetMetricsRecorder = assetMetricsRecorder;
        this.executionHandler = executionHandler;
        this.wrapForwardHandler = wrapForwardHandler;
        this.io = io;
    }

    boolean isOpenEntryPath(String requestPath) {
        // 即使 enabled=false 也要命中前缀，由 handleOpenEntry 返回 404，避免落入管理端 JWT
        String prefix = normalizeOpenPrefix(resolveOpenEntryPrefix());
        return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
    }

    private String resolveOpenEntryPrefix() {
        if (yuFlowRuntimeSettings != null) {
            return yuFlowRuntimeSettings.getOpenEntryPrefix();
        }
        return flowProperties.getOpen() != null
                ? flowProperties.getOpen().getEntryPrefix() : "/flow-api/open";
    }

    private static String normalizeOpenPrefix(String raw) {
        String p = StrUtil.isBlank(raw) ? "/flow-api/open" : raw.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    boolean shouldHandleDirectOpen(HttpServletRequest request) {
        boolean enabled = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.isOpenEnabled()
                : flowProperties.getOpen() != null && flowProperties.getOpen().isEnabled();
        boolean allowDirect = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.isOpenAllowDirectPath()
                : flowProperties.getOpen() != null && flowProperties.getOpen().isAllowDirectPath();
        if (!enabled || !allowDirect) {
            return false;
        }
        String appKey = request.getHeader(OpenAuthService.HDR_APP_KEY);
        return StrUtil.isNotBlank(appKey);
    }

    /**
     * 第三方开放入口：鉴权后按真实 path 匹配已发布 API 并执行（WRAP 则转发宿主）。
     */
    void handleOpenEntry(HttpServletRequest request, HttpServletResponse response,
                         FilterChain filterChain, String requestPath, String requestMethod)
            throws IOException, ServletException {
        boolean openEnabled = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.isOpenEnabled()
                : flowProperties.getOpen() != null && flowProperties.getOpen().isEnabled();
        if (!openEnabled) {
            io.writeJsonResponse(response, HttpStatus.NOT_FOUND.value(), R.fail(404, "开放入口未启用"));
            return;
        }
        String prefix = normalizeOpenPrefix(resolveOpenEntryPrefix());
        String realPath = requestPath.substring(prefix.length());
        if (StrUtil.isBlank(realPath)) {
            io.writeJsonResponse(response, HttpStatus.NOT_FOUND.value(),
                    R.fail(404, "开放入口缺少真实接口路径"));
            return;
        }
        if (!realPath.startsWith("/")) {
            realPath = "/" + realPath;
        }
        handleOpenOnRealPath(request, response, filterChain, realPath, requestMethod, null);
    }

    /**
     * 开放鉴权执行：{@code preMatched} 非空时跳过路由查找（直连真实 path 场景）。
     * <p>path 以 /export 结尾时：验签 path 含后缀，路由匹配剥离后缀后的业务 path，执行 Excel 导出。</p>
     * <p>WRAP：开放鉴权通过后按业务 path 转发宿主（需改写 request path，避免仍打到 /flow-api/open/**）。</p>
     */
    void handleOpenOnRealPath(HttpServletRequest request, HttpServletResponse response,
                              FilterChain filterChain, String realPath, String requestMethod,
                              FlowApiDO preMatched) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        HttpServletRequest effectiveRequest = wrapOpenBody(request);

        boolean excelExport = ApiExportPathSupport.isExportPath(realPath);
        String matchPath = excelExport ? ApiExportPathSupport.stripExportSuffix(realPath) : realPath;

        OpenAuthContext authCtx = null;
        FlowApiDO flowApiDO = preMatched;
        MetricsOutcome outcome = MetricsOutcome.FAIL;
        Integer httpStatus = null;
        String errorCode = null;
        try {
            // 验签使用完整 realPath（含 /export），与调用方签名一致
            authCtx = openAuthService.authenticate(effectiveRequest, realPath, requestMethod);

            if (flowApiDO == null) {
                flowApiDO = flowApiCacheManager.getExactMatch(requestMethod, matchPath);
                if (flowApiDO == null) {
                    FlowApiCacheManager.AntMatchResult matchResult =
                            flowApiCacheManager.getPatternMatch(requestMethod, matchPath, ANT_PATH_MATCHER);
                    if (matchResult != null) {
                        flowApiDO = matchResult.getApi();
                        if (matchResult.getPathVariables() != null && !matchResult.getPathVariables().isEmpty()) {
                            effectiveRequest.setAttribute("flowPathVariables", matchResult.getPathVariables());
                        }
                    }
                }
            }
            if (flowApiDO == null) {
                httpStatus = HttpStatus.NOT_FOUND.value();
                errorCode = "OPEN_API_NOT_FOUND";
                io.writeJsonResponse(response, httpStatus, R.fail(404, "开放接口不存在或未发布"));
                return;
            }

            openAuthService.assertApiGranted(authCtx, flowApiDO.getId(), requestMethod);

            String configMethod = flowApiDO.getMethod();
            if (!requestMethod.equalsIgnoreCase(configMethod)) {
                httpStatus = HttpStatus.METHOD_NOT_ALLOWED.value();
                errorCode = "OPEN_METHOD_NOT_ALLOWED";
                io.writeJsonResponse(response, httpStatus,
                        R.fail(HttpStatus.METHOD_NOT_ALLOWED.value(),
                                String.format("此接口不支持 %s 请求。请改用 %s 请求。", requestMethod, configMethod)));
                return;
            }

            effectiveRequest.setAttribute("yuOpenPlatformId", authCtx.getPlatformId());
            effectiveRequest.setAttribute("yuOpenAppKey", authCtx.getAppKey());

            if (PublishedApiSnapshot.isWrap(flowApiDO)) {
                if (excelExport) {
                    httpStatus = HttpStatus.BAD_REQUEST.value();
                    errorCode = "OPEN_WRAP_EXPORT_UNSUPPORTED";
                    io.writeJsonResponse(response, httpStatus,
                            R.fail(400, "宿主包裹接口不支持 /export 导出"));
                    return;
                }
                // 开放入口 path 是 /flow-api/open/{biz}，转发前改写为业务 path
                HttpServletRequest hostReq = wrapForwardHandler.alignRequestPath(effectiveRequest, matchPath);
                wrapForwardHandler.wrapForwardToHost(hostReq, response, filterChain, flowApiDO);
                httpStatus = response.getStatus() > 0 ? response.getStatus() : HttpStatus.OK.value();
                outcome = httpStatus < 400 ? MetricsOutcome.SUCCESS : MetricsOutcome.FAIL;
                if (outcome == MetricsOutcome.FAIL) {
                    errorCode = "OPEN_WRAP_HOST_FAIL";
                }
            } else if (excelExport) {
                outcome = executionHandler.executeExcelExport(effectiveRequest, response, flowApiDO);
                httpStatus = response.getStatus() > 0 ? response.getStatus() : HttpStatus.OK.value();
                if (outcome == MetricsOutcome.FAIL) {
                    errorCode = "OPEN_BIZ_FAIL";
                }
            } else {
                // API 维度由 executeApi / 短路径记账；此处仅取业务 outcome 供 PLATFORM
                outcome = executionHandler.executeAndWriteResponse(effectiveRequest, response, flowApiDO);
                httpStatus = response.getStatus() > 0 ? response.getStatus() : HttpStatus.OK.value();
                if (outcome == MetricsOutcome.FAIL) {
                    errorCode = "OPEN_BIZ_FAIL";
                }
            }
        } catch (OpenAuthException e) {
            httpStatus = e.getHttpStatus();
            errorCode = e.getCode();
            outcome = MetricsOutcome.AUTH_FAIL;
            if (authCtx == null) {
                authCtx = openAuthService.peekByAppKey(effectiveRequest.getHeader(OpenAuthService.HDR_APP_KEY));
            }
            io.writeJsonResponse(response, e.getHttpStatus(),
                    R.failWithErrorCode(e.getHttpStatus(), e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("[FlowApiGatewayFilter] 开放入口执行异常:\n{}", ThrowableUtil.getStackTrace(e));
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
            errorCode = "OPEN_INTERNAL_ERROR";
            if (excelExport) {
                executionHandler.handleExcelExportException(response, e);
                httpStatus = response.getStatus() > 0 ? response.getStatus() : httpStatus;
            } else if (flowApiDO != null) {
                executionHandler.handleExceptionResponse(response, flowApiDO, e);
            } else {
                io.writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        R.failWithErrorCode(500, errorCode, "开放入口内部错误：" + e.getMessage()));
            }
        } finally {
            long cost = System.currentTimeMillis() - start;
            // 仅记 PLATFORM，避免与 FlowApiServiceImpl 的 API 计量双重计数
            if (assetMetricsRecorder != null
                    && authCtx != null && StrUtil.isNotBlank(authCtx.getPlatformId())) {
                assetMetricsRecorder.record(MetricsAssetType.PLATFORM, authCtx.getPlatformId(), outcome, cost);
            }
            writeOpenCallLog(authCtx, flowApiDO, requestMethod, realPath, httpStatus, cost, errorCode, effectiveRequest);
        }
    }

    HttpServletRequest wrapOpenBody(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);
            boolean includeBodyHash = yuFlowRuntimeSettings != null
                    ? yuFlowRuntimeSettings.isOpenIncludeBodyHash()
                    : flowProperties.getOpen() != null && flowProperties.getOpen().isIncludeBodyHash();
            if (includeBodyHash && body.length > 0) {
                wrapped.setAttribute(OpenAuthService.ATTR_BODY_SHA256, DigestUtil.sha256Hex(body));
            } else {
                wrapped.setAttribute(OpenAuthService.ATTR_BODY_SHA256, "");
            }
            return wrapped;
        } catch (Exception e) {
            log.warn("[FlowApiGatewayFilter] 缓存开放请求体失败，HMAC bodyHash 将为空: {}", e.getMessage());
            request.setAttribute(OpenAuthService.ATTR_BODY_SHA256, "");
            return request;
        }
    }

    private void writeOpenCallLog(OpenAuthContext authCtx, FlowApiDO flowApiDO,
                                  String method, String realPath, Integer httpStatus,
                                  long costMs, String errorCode, HttpServletRequest request) {
        try {
            if (openAuthService == null || !openAuthService.shouldWriteCallLog(authCtx)) {
                return;
            }
            String appKey = authCtx != null ? authCtx.getAppKey() : request.getHeader(OpenAuthService.HDR_APP_KEY);
            String platformId = authCtx != null ? authCtx.getPlatformId() : null;
            if (StrUtil.isBlank(platformId) && StrUtil.isBlank(appKey)) {
                return;
            }
            OpenCallLogRecorder.saveAsync(FlowOpenCallLogDO.builder()
                    .platformId(platformId)
                    .appKey(appKey)
                    .apiId(flowApiDO != null ? flowApiDO.getId() : null)
                    .method(method)
                    .path(realPath)
                    .status(httpStatus)
                    .costMs(costMs)
                    .errorCode(errorCode)
                    .requestId(request.getHeader("X-Request-Id"))
                    .build());
        } catch (Exception e) {
            log.warn("[FlowApiGatewayFilter] 开放调用日志失败(fail-open): {}", e.getMessage());
        }
    }
}
