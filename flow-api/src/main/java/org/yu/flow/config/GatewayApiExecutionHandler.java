package org.yu.flow.config;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.config.response.ResponseStrategyResolver;
import org.yu.flow.config.response.ResponseTransformer;
import org.yu.flow.config.response.ResponseWrapperContext;
import org.yu.flow.dto.R;
import org.yu.flow.exception.SchemaValidationException;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.cache.ApiCacheConfig;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.ApiDataExportRequestDTO;
import org.yu.flow.module.api.privacy.EffectivePrivacy;
import org.yu.flow.module.api.privacy.PrivacyClass;
import org.yu.flow.module.api.privacy.PrivacyCryptoService;
import org.yu.flow.module.api.privacy.PrivacyDecision;
import org.yu.flow.module.api.privacy.PrivacyFieldInterceptor;
import org.yu.flow.module.api.security.IngressException;
import org.yu.flow.module.api.security.IngressSecurityResolver;
import org.yu.flow.module.api.service.ApiDataViewService;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.FlowHostRequestAttrs;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsKeys;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.util.ThrowableUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API 执行协作件：契约转换/校验、响应缓存、带硬超时执行、响应包装写出、Excel 导出与计量。
 * <p>从 {@link FlowApiGatewayFilter} 拆出，行为保持一致（日志前缀沿用 [FlowApiGatewayFilter]）。</p>
 */
@Slf4j
class GatewayApiExecutionHandler {

    private static final String CACHE_HEADER = "ss-flow-cache";

    /** 接口执行超时专用线程池（daemon，避免阻塞关闭） */
    private static final ExecutorService API_TIMEOUT_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "flow-api-timeout-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private final FlowApiExecutionService flowApiService;
    private final SchemaValidatorService schemaValidatorService;
    private final ContractParamTypeConverter contractParamTypeConverter;
    private final ResponseStrategyResolver responseStrategyResolver;
    private final ResponseTransformer responseTransformer;
    private final ApiResponseCacheService apiResponseCacheService;
    private final AssetMetricsRecorder assetMetricsRecorder;
    private final ApiDataViewService apiDataViewService;
    private final IngressSecurityResolver ingressSecurityResolver;
    private final PrivacyFieldInterceptor privacyFieldInterceptor;
    private final GatewayIo io;
    private final ObjectMapper objectMapper;
    private final YuFlowProperties flowProperties;
    private final YuFlowRuntimeSettings yuFlowRuntimeSettings;

    GatewayApiExecutionHandler(FlowApiExecutionService flowApiService,
                               SchemaValidatorService schemaValidatorService,
                               ContractParamTypeConverter contractParamTypeConverter,
                               ResponseStrategyResolver responseStrategyResolver,
                               ResponseTransformer responseTransformer,
                               ApiResponseCacheService apiResponseCacheService,
                               AssetMetricsRecorder assetMetricsRecorder,
                               ApiDataViewService apiDataViewService,
                               IngressSecurityResolver ingressSecurityResolver,
                               GatewayIo io,
                               ObjectMapper objectMapper) {
        this(flowApiService, schemaValidatorService, contractParamTypeConverter, responseStrategyResolver,
                responseTransformer, apiResponseCacheService, assetMetricsRecorder, apiDataViewService,
                ingressSecurityResolver, null, io, objectMapper, null, null);
    }

    GatewayApiExecutionHandler(FlowApiExecutionService flowApiService,
                               SchemaValidatorService schemaValidatorService,
                               ContractParamTypeConverter contractParamTypeConverter,
                               ResponseStrategyResolver responseStrategyResolver,
                               ResponseTransformer responseTransformer,
                               ApiResponseCacheService apiResponseCacheService,
                               AssetMetricsRecorder assetMetricsRecorder,
                               ApiDataViewService apiDataViewService,
                               IngressSecurityResolver ingressSecurityResolver,
                               PrivacyFieldInterceptor privacyFieldInterceptor,
                               GatewayIo io,
                               ObjectMapper objectMapper) {
        this(flowApiService, schemaValidatorService, contractParamTypeConverter, responseStrategyResolver,
                responseTransformer, apiResponseCacheService, assetMetricsRecorder, apiDataViewService,
                ingressSecurityResolver, privacyFieldInterceptor, io, objectMapper, null, null);
    }

    GatewayApiExecutionHandler(FlowApiExecutionService flowApiService,
                               SchemaValidatorService schemaValidatorService,
                               ContractParamTypeConverter contractParamTypeConverter,
                               ResponseStrategyResolver responseStrategyResolver,
                               ResponseTransformer responseTransformer,
                               ApiResponseCacheService apiResponseCacheService,
                               AssetMetricsRecorder assetMetricsRecorder,
                               ApiDataViewService apiDataViewService,
                               IngressSecurityResolver ingressSecurityResolver,
                               PrivacyFieldInterceptor privacyFieldInterceptor,
                               GatewayIo io,
                               ObjectMapper objectMapper,
                               YuFlowProperties flowProperties) {
        this(flowApiService, schemaValidatorService, contractParamTypeConverter, responseStrategyResolver,
                responseTransformer, apiResponseCacheService, assetMetricsRecorder, apiDataViewService,
                ingressSecurityResolver, privacyFieldInterceptor, io, objectMapper, flowProperties, null);
    }

    GatewayApiExecutionHandler(FlowApiExecutionService flowApiService,
                               SchemaValidatorService schemaValidatorService,
                               ContractParamTypeConverter contractParamTypeConverter,
                               ResponseStrategyResolver responseStrategyResolver,
                               ResponseTransformer responseTransformer,
                               ApiResponseCacheService apiResponseCacheService,
                               AssetMetricsRecorder assetMetricsRecorder,
                               ApiDataViewService apiDataViewService,
                               IngressSecurityResolver ingressSecurityResolver,
                               PrivacyFieldInterceptor privacyFieldInterceptor,
                               GatewayIo io,
                               ObjectMapper objectMapper,
                               YuFlowProperties flowProperties,
                               YuFlowRuntimeSettings yuFlowRuntimeSettings) {
        this.flowApiService = flowApiService;
        this.schemaValidatorService = schemaValidatorService;
        this.contractParamTypeConverter = contractParamTypeConverter;
        this.responseStrategyResolver = responseStrategyResolver;
        this.responseTransformer = responseTransformer;
        this.apiResponseCacheService = apiResponseCacheService;
        this.assetMetricsRecorder = assetMetricsRecorder;
        this.apiDataViewService = apiDataViewService;
        this.ingressSecurityResolver = ingressSecurityResolver;
        this.privacyFieldInterceptor = privacyFieldInterceptor;
        this.io = io;
        this.objectMapper = objectMapper;
        this.flowProperties = flowProperties;
        this.yuFlowRuntimeSettings = yuFlowRuntimeSettings;
    }

    /**
     * @return 业务计量结果（供开放入口 PLATFORM 维度使用）；API 维度由本方法短路径或 {@code executeApi} 记账
     */
    MetricsOutcome executeAndWriteResponse(HttpServletRequest request, HttpServletResponse response,
                                           FlowApiDO flowApiDO) throws Exception {
        long gateStart = System.currentTimeMillis();
        // 1. 提取所有请求参数
        Map<String, String> queryParams = io.extractQueryParams(request);
        Map<String, Object> bodyParams = io.extractBodyParams(request);
        Map<String, String> headers = io.extractHeaders(request);
        Map<String, Object> pathParams = io.toObjectMap(request.getAttribute("flowPathVariables"));

        // 2. 提取分页对象
        Pageable pageable = io.extractPageable(request);

        // 3. 根据已发布契约转换参数类型，并校验 Body / Query / Path / Headers。
        String contractRule = PublishedApiSnapshot.resolveContract(flowApiDO);
        Map<String, Object> typedQueryParams;
        Map<String, Object> typedBodyParams;
        Map<String, Object> typedHeaders;
        Map<String, Object> typedPathParams;
        if (StrUtil.isNotBlank(contractRule)) {
            try {
                typedQueryParams = contractParamTypeConverter.convertSection(contractRule, "query", queryParams);
                typedBodyParams = contractParamTypeConverter.convertSection(contractRule, "body", bodyParams);
                typedHeaders = contractParamTypeConverter.convertSection(contractRule, "headers", headers);
                typedPathParams = contractParamTypeConverter.convertSection(contractRule, "pathParams", pathParams);
                schemaValidatorService.validateFromContract(
                        contractRule, typedBodyParams, typedQueryParams, typedPathParams, typedHeaders);
            } catch (SchemaValidationException e) {
                io.writeJsonResponse(response, HttpStatus.BAD_REQUEST.value(),
                        R.fail(400, e.getMessage()));
                recordApiMetricsIfNeeded(request, flowApiDO, MetricsOutcome.FAIL, gateStart);
                return MetricsOutcome.FAIL;
            }
        } else {
            typedQueryParams = new LinkedHashMap<>(queryParams);
            typedBodyParams = new LinkedHashMap<>(bodyParams);
            typedHeaders = new LinkedHashMap<>(headers);
            typedPathParams = pathParams;
        }

        Map<String, Object> inputParamsMap = new HashMap<>(8);
        inputParamsMap.put("@QP", typedQueryParams);
        inputParamsMap.put("@BP", typedBodyParams);
        inputParamsMap.put("@PP", typedPathParams);
        // 兼容 Request 节点
        inputParamsMap.put("headers", typedHeaders);
        inputParamsMap.put("params", typedQueryParams);
        inputParamsMap.put("body", typedBodyParams);
        // 入站解析的调用方身份（HOST / OPEN 合成主体）
        FlowHostPrincipal hostPrincipal = FlowHostRequestAttrs.getPrincipal(request);
        if (hostPrincipal != null) {
            inputParamsMap.put("@AUTH", FlowHostRequestAttrs.toAuthContext(hostPrincipal));
        }

        // 4. 响应缓存：命中则跳过执行
        ApiCacheConfig cacheConfig = apiResponseCacheService.parseConfig(flowApiDO.getCacheConfig());
        String cacheKey = null;
        boolean cacheEnabled = apiResponseCacheService.isEnabled(cacheConfig);
        EffectivePrivacy privacy = privacyFieldInterceptor == null
                ? EffectivePrivacy.disabled()
                : privacyFieldInterceptor.resolveConfig(flowApiDO, false);
        PrivacyDecision privacyDecision = privacyFieldInterceptor == null
                ? PrivacyDecision.mask()
                : privacyFieldInterceptor.resolveDecision(hostPrincipal, privacy);
        PrivacyClass privacyClass = privacyDecision.getPrivacyClass();
        boolean privacyReveal = privacy.isEnabled() && (privacyClass == PrivacyClass.REVEAL
                || privacyDecision.getFieldActions().containsValue(PrivacyDecision.ACTION_REVEAL));
        if (privacyReveal) {
            cacheEnabled = false;
        }
        if (cacheEnabled) {
            cacheKey = apiResponseCacheService.buildCacheKey(
                    flowApiDO.getId(), cacheConfig,
                    typedQueryParams, typedBodyParams, typedPathParams, typedHeaders, pageable);
            if (privacy.isEnabled()) {
                cacheKey = cacheKey + privacyDecision.cacheSuffix();
            }
            String cachedJson = apiResponseCacheService.get(cacheKey);
            if (cachedJson != null) {
                response.setContentType(GatewayIo.JSON_CONTENT_TYPE);
                response.setCharacterEncoding("UTF-8");
                response.setStatus(HttpStatus.OK.value());
                response.setHeader("ss-flow", "yes");
                response.setHeader(CACHE_HEADER, "HIT");
                response.getWriter().write(cachedJson);
                recordApiMetricsIfNeeded(request, flowApiDO, MetricsOutcome.SUCCESS, gateStart);
                return MetricsOutcome.SUCCESS;
            }
        }

        // 5. 执行 API（可按全局/接口 timeoutMs 硬中断；resolver 未装配时视为不限时）
        Object result;
        try {
            int timeoutMs = ingressSecurityResolver != null
                    ? ingressSecurityResolver.resolve(flowApiDO).getTimeoutMs() : 0;
            result = executeApiWithTimeout(flowApiDO, inputParamsMap, pageable, response, timeoutMs);
        } catch (IngressException te) {
            io.writeJsonResponse(response, te.getHttpStatus(),
                    R.failWithErrorCode(te.getHttpStatus(), te.getCode(), te.getMessage()));
            recordApiMetricsIfNeeded(request, flowApiDO, MetricsOutcome.FAIL, gateStart);
            return MetricsOutcome.FAIL;
        }
        request.setAttribute("yuApiMetricsByService", Boolean.TRUE);

        // 6. 获取包装上下文
        ResponseWrapperContext context = responseStrategyResolver.resolve(flowApiDO);

        // 7. 写出响应
        response.setContentType(GatewayIo.JSON_CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpStatus.OK.value());
        response.setHeader("ss-flow", "yes");

        boolean businessFail = result instanceof R && !((R<?>) result).getOk();
        if (result instanceof ResponseEntity) {
            io.writeResponseEntity(response, (ResponseEntity<?>) result);
        } else {
            String templateToUse;
            if (businessFail) {
                templateToUse = context.getFailWrapper();
            } else if (isPageResponse(result) || "PAGE".equalsIgnoreCase(flowApiDO.getResponseType())) {
                templateToUse = context.getPageWrapper();
            } else {
                templateToUse = context.getSuccessWrapper();
            }

            if (privacyFieldInterceptor != null && privacy.isEnabled() && !businessFail) {
                boolean wrapTransport = isPublishedJsonWrapTransport();
                byte[] transportKey = null;
                if (privacyReveal && wrapTransport) {
                    transportKey = privacyFieldInterceptor.unwrapTransportKey(
                            request.getHeader(PrivacyCryptoService.HEADER_PRIVACY_KEY));
                }
                result = privacyFieldInterceptor.apply(result, privacy, privacyDecision, transportKey, wrapTransport);
            }

            Object finalResult = responseTransformer.transform(result, templateToUse);
            if (cacheEnabled && !businessFail) {
                try {
                    String json = objectMapper.writeValueAsString(finalResult);
                    apiResponseCacheService.put(flowApiDO.getId(), cacheKey, json, cacheConfig);
                    response.setHeader(CACHE_HEADER, "MISS");
                    response.getWriter().write(json);
                    return businessFail ? MetricsOutcome.FAIL : MetricsOutcome.SUCCESS;
                } catch (Exception e) {
                    log.warn("[FlowApiGatewayFilter] 写入响应缓存失败(fail-open): apiId={}, error={}",
                            flowApiDO.getId(), e.getMessage());
                }
            }
            if (cacheEnabled) {
                response.setHeader(CACHE_HEADER, "MISS");
            }
            objectMapper.writeValue(response.getWriter(), finalResult);
        }
        return businessFail ? MetricsOutcome.FAIL : MetricsOutcome.SUCCESS;
    }

    /**
     * 带硬超时的 API 执行。{@code timeoutMs ≤ 0} 时同步执行、不限制。
     */
    private Object executeApiWithTimeout(FlowApiDO flowApiDO, Map<String, Object> inputParamsMap,
                                         Pageable pageable, HttpServletResponse response,
                                         int timeoutMs) throws Exception {
        if (timeoutMs <= 0) {
            return flowApiService.executeApi(flowApiDO, inputParamsMap, pageable, response);
        }
        Future<Object> future = API_TIMEOUT_EXECUTOR.submit(
                () -> flowApiService.executeApi(flowApiDO, inputParamsMap, pageable, response));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[FlowApiGatewayFilter] API 执行超时: apiId={}, timeoutMs={}",
                    flowApiDO.getId(), timeoutMs);
            throw IngressException.apiTimeout(timeoutMs);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new Exception(cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw IngressException.apiTimeout(timeoutMs);
        }
    }

    /**
     * 对外 /export：复用已发布快照导出；未开启 openExportEnabled 统一 404。
     */
    MetricsOutcome executeExcelExport(HttpServletRequest request, HttpServletResponse response,
                                      FlowApiDO flowApiDO) throws Exception {
        long gateStart = System.currentTimeMillis();
        ApiDataExportRequestDTO exportReq = new ApiDataExportRequestDTO();
        exportReq.setUseDraft(false);
        exportReq.setQueryParams(io.extractQueryParams(request));
        exportReq.setBodyParams(io.extractBodyParams(request));
        Map<String, Object> pathObj = io.toObjectMap(request.getAttribute("flowPathVariables"));
        Map<String, String> pathParams = new LinkedHashMap<>();
        if (pathObj != null) {
            pathObj.forEach((k, v) -> pathParams.put(k, v == null ? null : String.valueOf(v)));
        }
        exportReq.setPathParams(pathParams);

        try {
            apiDataViewService.exportPublishedOpenExcel(flowApiDO, exportReq, response);
            recordApiMetricsIfNeeded(request, flowApiDO, MetricsOutcome.SUCCESS, gateStart, MetricsKeys.TRIGGER_EXPORT);
            return MetricsOutcome.SUCCESS;
        } catch (ValidationException e) {
            handleExcelExportException(response, e);
            recordApiMetricsIfNeeded(request, flowApiDO, MetricsOutcome.FAIL, gateStart, MetricsKeys.TRIGGER_EXPORT);
            return MetricsOutcome.FAIL;
        }
    }

    void handleExcelExportException(HttpServletResponse response, Exception e) throws IOException {
        if (e instanceof ValidationException) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            // 未开启对外导出 / 类型不支持：统一 404，避免探测
            if (msg.contains("未启用对外") || msg.contains("不支持数据查看") || msg.contains("仅支持")
                    || msg.contains("接口不存在") || msg.contains("已关闭数据导出")) {
                io.writeJsonResponse(response, HttpStatus.NOT_FOUND.value(), R.fail(404, "接口不存在"));
                return;
            }
            io.writeJsonResponse(response, HttpStatus.BAD_REQUEST.value(), R.fail(400, msg));
            return;
        }
        log.error("[FlowApiGatewayFilter] Excel 导出异常:\n{}", ThrowableUtil.getStackTrace(e));
        io.writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                R.fail(500, "Excel 导出失败：" + e.getMessage()));
    }

    void handleExceptionResponse(HttpServletResponse response, FlowApiDO flowApiDO, Exception e) throws IOException {
        R<Object> r = R.fail(500, e.getMessage() != null ? e.getMessage() : "系统内部错误，请联系管理员");
        ResponseWrapperContext context = responseStrategyResolver.resolve(flowApiDO);
        Object finalResult = responseTransformer.transform(r, context.getFailWrapper());
        io.writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), finalResult);
    }

    void recordApiMetricsIfNeeded(HttpServletRequest request, FlowApiDO flowApiDO,
                                  MetricsOutcome outcome, long startMs) {
        recordApiMetricsIfNeeded(request, flowApiDO, outcome, startMs, MetricsKeys.TRIGGER_DEFAULT);
    }

    void recordApiMetricsIfNeeded(HttpServletRequest request, FlowApiDO flowApiDO,
                                  MetricsOutcome outcome, long startMs, String trigger) {
        if (assetMetricsRecorder == null || flowApiDO == null || flowApiDO.getId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(request.getAttribute("yuApiMetricsByService"))) {
            return;
        }
        assetMetricsRecorder.record(MetricsAssetType.API, flowApiDO.getId(), outcome,
                System.currentTimeMillis() - startMs, trigger);
        request.setAttribute("yuApiMetricsByService", Boolean.TRUE);
    }

    private boolean isPageResponse(Object result) {
        if (result == null) {
            return false;
        }
        if (result instanceof org.springframework.data.domain.Page) {
            return true;
        }
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            return map.containsKey("items") && map.containsKey("total") && map.containsKey("current");
        }
        return false;
    }

    /**
     * 已发布 JSON 是否套传输信封。系统参数优先，未注入运行时配置时回退 yml，再回退 true。
     */
    private boolean isPublishedJsonWrapTransport() {
        if (yuFlowRuntimeSettings != null) {
            return yuFlowRuntimeSettings.isPrivacyWrapTransport();
        }
        if (flowProperties != null && flowProperties.getPrivacy() != null) {
            return flowProperties.getPrivacy().isWrapTransport();
        }
        return true;
    }
}
