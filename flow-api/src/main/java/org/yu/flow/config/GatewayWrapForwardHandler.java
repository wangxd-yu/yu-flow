package org.yu.flow.config;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UrlPathHelper;
import org.yu.flow.log.execution.service.FlowExecutionLogService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.HostBindingConfig;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsKeys;
import org.yu.flow.module.metrics.MetricsOutcome;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * WRAP 同名包裹协作件：受控转发宿主 FilterChain、计量与可选执行日志、开放入口 path 改写。
 * <p>从 {@link FlowApiGatewayFilter} 拆出，行为保持一致（日志前缀沿用 [FlowApiGatewayFilter]）。</p>
 */
@Slf4j
class GatewayWrapForwardHandler {

    /** WRAP 防重入标记：已进入受控转发，后续再匹配到本 Filter 时直接放行 */
    static final String ATTR_HOST_WRAP_FORWARDED = "yu.flow.host-wrap.forwarded";

    private final AssetMetricsRecorder assetMetricsRecorder;
    private final FlowExecutionLogService flowExecutionLogService;
    private final UrlPathHelper urlPathHelper;

    GatewayWrapForwardHandler(AssetMetricsRecorder assetMetricsRecorder,
                              FlowExecutionLogService flowExecutionLogService,
                              UrlPathHelper urlPathHelper) {
        this.assetMetricsRecorder = assetMetricsRecorder;
        this.flowExecutionLogService = flowExecutionLogService;
        this.urlPathHelper = urlPathHelper;
    }

    /**
     * WRAP：受控转发宿主 FilterChain，透传响应；异步记计量/可选执行日志（默认不落 body）。
     */
    void wrapForwardToHost(HttpServletRequest request, HttpServletResponse response,
                           FilterChain filterChain, FlowApiDO flowApiDO)
            throws IOException, ServletException {
        long startNs = System.nanoTime();
        request.setAttribute(ATTR_HOST_WRAP_FORWARDED, Boolean.TRUE);
        boolean forwardedOk = false;
        try {
            filterChain.doFilter(request, response);
            forwardedOk = true;
        } finally {
            long costMs = (System.nanoTime() - startNs) / 1_000_000L;
            int status = response.getStatus();
            boolean success = forwardedOk && status > 0 && status < 400;
            if (assetMetricsRecorder != null && flowApiDO.getId() != null) {
                try {
                    assetMetricsRecorder.record(
                            MetricsAssetType.API,
                            flowApiDO.getId(),
                            success ? MetricsOutcome.SUCCESS : MetricsOutcome.FAIL,
                            costMs,
                            MetricsKeys.TRIGGER_DEFAULT);
                } catch (Exception metricEx) {
                    log.debug("[FlowApiGatewayFilter] WRAP 计量失败 apiId={}", flowApiDO.getId(), metricEx);
                }
            }
            if (shouldWriteWrapLog(flowApiDO, success) && flowExecutionLogService != null) {
                try {
                    org.yu.flow.log.execution.domain.FlowExecutionLogDO logDO =
                            new org.yu.flow.log.execution.domain.FlowExecutionLogDO();
                    logDO.setApiId(flowApiDO.getId());
                    logDO.setApiName(PublishedApiSnapshot.resolveName(flowApiDO));
                    logDO.setUrl(PublishedApiSnapshot.resolveUrl(flowApiDO));
                    logDO.setServiceType(PublishedApiSnapshot.resolveServiceType(flowApiDO));
                    logDO.setMethod(PublishedApiSnapshot.resolveMethod(flowApiDO));
                    logDO.setStatus(success ? "SUCCESS" : "FAIL");
                    logDO.setCostTimeMs(costMs);
                    if (!success) {
                        logDO.setErrorMsg("host wrap status=" + status);
                    }
                    // 性能：默认不落 request/response body
                    flowExecutionLogService.saveLogAsync(logDO);
                } catch (Exception logEx) {
                    log.debug("[FlowApiGatewayFilter] WRAP 日志失败 apiId={}", flowApiDO.getId(), logEx);
                }
            }
        }
    }

    /** logEnabled + hostBinding.logMode（ALL / ERROR_ONLY / SAMPLE） */
    private static boolean shouldWriteWrapLog(FlowApiDO api, boolean success) {
        if (api == null || !Boolean.TRUE.equals(api.getLogEnabled())) {
            return false;
        }
        String bindingJson = PublishedApiSnapshot.resolveHostBinding(api);
        return HostBindingConfig.parse(bindingJson).shouldLog(success);
    }

    /**
     * 将请求查找 path 对齐到业务 path（开放入口 /flow-api/open/{biz} → {biz}）。
     */
    HttpServletRequest alignRequestPath(HttpServletRequest request, String businessPath) {
        if (request == null || StrUtil.isBlank(businessPath)) {
            return request;
        }
        String current = urlPathHelper.getPathWithinApplication(request);
        if (businessPath.equals(current)) {
            return request;
        }
        final String target = businessPath.startsWith("/") ? businessPath : "/" + businessPath;
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                String ctx = request.getContextPath();
                return (ctx == null ? "" : ctx) + target;
            }

            @Override
            public String getServletPath() {
                return target;
            }

            @Override
            public String getPathInfo() {
                return null;
            }
        };
    }
}
