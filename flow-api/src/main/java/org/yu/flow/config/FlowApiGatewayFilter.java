package org.yu.flow.config;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.response.ResponseStrategyResolver;
import org.yu.flow.config.response.ResponseTransformer;
import org.yu.flow.config.response.ResponseWrapperContext;
import org.yu.flow.dto.R;
import org.yu.flow.dto.ResultCode;
import org.yu.flow.exception.SchemaValidationException;
import org.yu.flow.module.api.cache.ApiCacheConfig;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.security.IngressException;
import org.yu.flow.module.api.security.IngressSecurityGuard;
import org.yu.flow.module.api.security.IngressSecurityResolver;
import org.yu.flow.module.api.security.IngressAuthMode;
import org.yu.flow.module.api.security.EffectiveSecurity;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.log.open.domain.FlowOpenCallLogDO;
import org.yu.flow.log.open.support.OpenCallLogRecorder;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthContext;
import org.yu.flow.module.open.auth.OpenAuthException;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.module.open.support.CachedBodyHttpServletRequest;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.util.FlowObjectMapperUtil;
import org.yu.flow.util.ThrowableUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * @author yu-flow
 */
@Slf4j
public class FlowApiGatewayFilter extends OncePerRequestFilter {

    private static final String SECURITY_LEVEL_HEADER = "ss-level";
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";
    private static final String CACHE_HEADER = "ss-flow-cache";
    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

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

    private final YuFlowProperties flowProperties;
    private final FlowApiExecutionService flowApiService;
    private final FlowApiCacheManager flowApiCacheManager;
    private final SchemaValidatorService schemaValidatorService;
    private final ContractParamTypeConverter contractParamTypeConverter;
    private final ResponseStrategyResolver responseStrategyResolver;
    private final ResponseTransformer responseTransformer;
    private final ApiResponseCacheService apiResponseCacheService;
    private final OpenAuthService openAuthService;
    private final AssetMetricsRecorder assetMetricsRecorder;
    private final HostAuthenticationProbe hostAuthenticationProbe;
    private final IngressSecurityResolver ingressSecurityResolver;
    private final IngressSecurityGuard ingressSecurityGuard;
    private final YuFlowRuntimeSettings yuFlowRuntimeSettings;

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();
    private final UrlPathHelper urlPathHelper = createUrlPathHelper();

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
                                YuFlowRuntimeSettings yuFlowRuntimeSettings) {
        this.flowProperties = flowProperties;
        this.flowApiService = flowApiService;
        this.flowApiCacheManager = flowApiCacheManager;
        this.schemaValidatorService = schemaValidatorService;
        this.contractParamTypeConverter = contractParamTypeConverter;
        this.responseStrategyResolver = responseStrategyResolver;
        this.responseTransformer = responseTransformer;
        this.apiResponseCacheService = apiResponseCacheService;
        this.openAuthService = openAuthService;
        this.assetMetricsRecorder = assetMetricsRecorder;
        this.hostAuthenticationProbe = hostAuthenticationProbe;
        this.ingressSecurityResolver = ingressSecurityResolver;
        this.ingressSecurityGuard = ingressSecurityGuard;
        this.yuFlowRuntimeSettings = yuFlowRuntimeSettings;
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

        String requestPath = urlPathHelper.getPathWithinApplication(request);

        // 2. 排除无需过滤的静态页面及 UI 路由路径
        if (requestPath.startsWith("/flow-ui/") || requestPath.equals("/flow-ui.html")) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestMethod = request.getMethod();

        // 2.5 第三方开放入口：/flow-api/open/{真实path}（不走管理端 JWT）
        if (isOpenEntryPath(requestPath)) {
            handleOpenEntry(request, response, requestPath, requestMethod);
            return;
        }

        // 3. 管理端鉴权（含 /flow-api/v3/api-docs* OpenAPI 契约）
        if (requestPath.startsWith("/flow-api")) {
            if (requestPath.startsWith("/flow-api/login")
                    || requestPath.startsWith("/flow-api/login/captcha")) {
                filterChain.doFilter(request, response);
                return;
            }
            String token = JwtTokenUtil.resolveToken(request);
            if (token == null) {
                writeJsonResponse(response, HttpStatus.UNAUTHORIZED.value(),
                        R.fail(ResultCode.TOKEN_EMPTY.getCode(), "token 不能为空！"));
                return;
            }
            try {
                JwtTokenUtil.validateToken(token);
            } catch (ValidateException e) {
                writeJsonResponse(response, HttpStatus.UNAUTHORIZED.value(),
                        R.fail(ResultCode.TOKEN_INVALID.getCode(), "token 已失效！"));
                return;
            }


        }

        try {
            // 4. 路由匹配（纯内存，零网络 I/O）
            FlowApiDO flowApiDO = flowApiCacheManager.getExactMatch(requestMethod, requestPath);

            // 精确未命中 → Ant 模式匹配 O(N)
            if (flowApiDO == null) {
                FlowApiCacheManager.AntMatchResult matchResult =
                        flowApiCacheManager.getPatternMatch(requestMethod, requestPath, ANT_PATH_MATCHER);
                if (matchResult != null) {
                    flowApiDO = matchResult.getApi();
                    if (matchResult.getPathVariables() != null && !matchResult.getPathVariables().isEmpty()) {
                        request.setAttribute("flowPathVariables", matchResult.getPathVariables());
                    }
                }
            }

            // 未匹配到动态路由（可能是宿主系统接口），放行
            if (flowApiDO == null) {
                filterChain.doFilter(request, response);
                return;
            }

            // 4.5 可选：真实 path + AppKey → 开放鉴权（凭证头分流，默认关）
            if (shouldHandleDirectOpen(request)) {
                handleOpenOnRealPath(request, response, requestPath, requestMethod, flowApiDO);
                return;
            }

            // 4.6 入站防护（全局+按接口）；关闭时仍强制管理端 JWT（禁止已发布 API 匿名裸奔）
            HttpServletRequest effectiveRequest = request;
            if (isIngressEnabled()) {
                try {
                    EffectiveSecurity sec = ingressSecurityResolver.resolve(flowApiDO);
                    if (sec.getAuthMode() == IngressAuthMode.OPEN) {
                        effectiveRequest = wrapOpenBody(request);
                    }
                    OpenAuthContext ingressCtx = ingressSecurityGuard.enforce(
                            effectiveRequest, flowApiDO, requestPath, requestMethod);
                    if (ingressCtx != null) {
                        effectiveRequest.setAttribute("yuOpenPlatformId", ingressCtx.getPlatformId());
                        effectiveRequest.setAttribute("yuOpenAppKey", ingressCtx.getAppKey());
                    }
                } catch (IngressException e) {
                    writeJsonResponse(response, e.getHttpStatus(),
                            R.failWithErrorCode(e.getHttpStatus(), e.getCode(), e.getMessage()));
                    return;
                }
            } else if (!assertManagementJwt(request, response)) {
                return;
            } else if (!assertHostAuthIfRequired(request, response)) {
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
                writeJsonResponse(response, HttpStatus.METHOD_NOT_ALLOWED.value(),
                        R.fail(HttpStatus.METHOD_NOT_ALLOWED.value(),
                                String.format("此接口不支持 %s 请求。请改用 %s 请求。", requestMethod, configMethod)));
                return;
            }

            // 7. 执行业务逻辑
            try {
                executeAndWriteResponse(effectiveRequest, response, flowApiDO);
            } catch (Exception e) {
                log.error("[FlowApiGatewayFilter] API 业务执行异常:\n{}", ThrowableUtil.getStackTrace(e));
                handleExceptionResponse(response, flowApiDO, e);
                // executeApi 未跑通时补记 FAIL（已执行路径由 FlowApiServiceImpl 记账）
                if (assetMetricsRecorder != null && flowApiDO.getId() != null
                        && request.getAttribute("yuApiMetricsByService") == null) {
                    assetMetricsRecorder.record(MetricsAssetType.API, flowApiDO.getId(),
                            MetricsOutcome.FAIL, 0L);
                }
            }

            // 执行完毕，直接 return 中断 FilterChain，绝不进入宿主应用逻辑
            return;

        } catch (Exception fatalEx) {
            log.error("[FlowApiGatewayFilter] 网关发生意外错误:", fatalEx);
            writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    R.fail(500, "网关内部系统错误：" + fatalEx.getMessage()));
        }
    }

    private void handleExceptionResponse(HttpServletResponse response, FlowApiDO flowApiDO, Exception e) throws IOException {
        R<Object> r = R.fail(500, e.getMessage() != null ? e.getMessage() : "系统内部错误，请联系管理员");
        ResponseWrapperContext context = responseStrategyResolver.resolve(flowApiDO);
        Object finalResult = responseTransformer.transform(r, context.getFailWrapper());
        writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), finalResult);
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
     * @return 业务计量结果（供开放入口 PLATFORM 维度使用）；API 维度由本方法短路径或 {@code executeApi} 记账
     */
    private MetricsOutcome executeAndWriteResponse(HttpServletRequest request, HttpServletResponse response,
                                                   FlowApiDO flowApiDO) throws Exception {
        long gateStart = System.currentTimeMillis();
        // 1. 提取所有请求参数
        Map<String, String> queryParams = extractQueryParams(request);
        Map<String, Object> bodyParams = extractBodyParams(request);
        Map<String, String> headers = extractHeaders(request);
        Map<String, Object> pathParams = toObjectMap(request.getAttribute("flowPathVariables"));

        // 2. 提取分页对象
        Pageable pageable = extractPageable(request);

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
                writeJsonResponse(response, HttpStatus.BAD_REQUEST.value(),
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

        // 4. 响应缓存：命中则跳过执行
        ApiCacheConfig cacheConfig = apiResponseCacheService.parseConfig(flowApiDO.getCacheConfig());
        String cacheKey = null;
        boolean cacheEnabled = apiResponseCacheService.isEnabled(cacheConfig);
        if (cacheEnabled) {
            cacheKey = apiResponseCacheService.buildCacheKey(
                    flowApiDO.getId(), cacheConfig,
                    typedQueryParams, typedBodyParams, typedPathParams, typedHeaders, pageable);
            String cachedJson = apiResponseCacheService.get(cacheKey);
            if (cachedJson != null) {
                response.setContentType(JSON_CONTENT_TYPE);
                response.setCharacterEncoding("UTF-8");
                response.setStatus(HttpStatus.OK.value());
                response.setHeader("ss-flow", "yes");
                response.setHeader(CACHE_HEADER, "HIT");
                response.getWriter().write(cachedJson);
                recordApiMetricsIfNeeded(request, flowApiDO, MetricsOutcome.SUCCESS, gateStart);
                return MetricsOutcome.SUCCESS;
            }
        }

        // 5. 执行 API（可按全局/接口 timeoutMs 硬中断）
        Object result;
        try {
            int timeoutMs = ingressSecurityResolver.resolve(flowApiDO).getTimeoutMs();
            result = executeApiWithTimeout(flowApiDO, inputParamsMap, pageable, response, timeoutMs);
        } catch (IngressException te) {
            writeJsonResponse(response, te.getHttpStatus(),
                    R.failWithErrorCode(te.getHttpStatus(), te.getCode(), te.getMessage()));
            recordApiMetricsIfNeeded(request, flowApiDO, MetricsOutcome.FAIL, gateStart);
            return MetricsOutcome.FAIL;
        }
        request.setAttribute("yuApiMetricsByService", Boolean.TRUE);

        // 6. 获取包装上下文
        ResponseWrapperContext context = responseStrategyResolver.resolve(flowApiDO);

        // 7. 写出响应
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpStatus.OK.value());
        response.setHeader("ss-flow", "yes");

        boolean businessFail = result instanceof R && !((R<?>) result).getOk();
        if (result instanceof ResponseEntity) {
            writeResponseEntity(response, (ResponseEntity<?>) result);
        } else {
            String templateToUse;
            if (businessFail) {
                templateToUse = context.getFailWrapper();
            } else if (isPageResponse(result) || "PAGE".equalsIgnoreCase(flowApiDO.getResponseType())) {
                templateToUse = context.getPageWrapper();
            } else {
                templateToUse = context.getSuccessWrapper();
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

    private void recordApiMetricsIfNeeded(HttpServletRequest request, FlowApiDO flowApiDO,
                                          MetricsOutcome outcome, long startMs) {
        if (assetMetricsRecorder == null || flowApiDO == null || flowApiDO.getId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(request.getAttribute("yuApiMetricsByService"))) {
            return;
        }
        assetMetricsRecorder.record(MetricsAssetType.API, flowApiDO.getId(), outcome,
                System.currentTimeMillis() - startMs);
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

    private void writeResponseEntity(HttpServletResponse response, ResponseEntity<?> responseEntity) throws IOException {
        response.setStatus(responseEntity.getStatusCodeValue());
        responseEntity.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                response.setHeader(name, value);
            }
        });

        Object body = responseEntity.getBody();
        if (body == null) {
            return;
        }
        if (body instanceof String) {
            if (!responseEntity.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE)) {
                response.setContentType("text/plain;charset=UTF-8");
            }
            response.getWriter().write((String) body);
        } else {
            objectMapper.writeValue(response.getWriter(), body);
        }
    }

    private void writeJsonResponse(HttpServletResponse response, int status, Object data) throws IOException {
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status);
        objectMapper.writeValue(response.getWriter(), data);
    }

    private boolean isOpenEntryPath(String requestPath) {
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

    private boolean shouldHandleDirectOpen(HttpServletRequest request) {
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

    private boolean isIngressEnabled() {
        boolean enabled = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.isIngressEnabled()
                : flowProperties.getIngress() != null && flowProperties.getIngress().isEnabled();
        return enabled && ingressSecurityResolver != null && ingressSecurityGuard != null;
    }

    /**
     * 已发布动态 API 在未启用 ingress 时的安全默认：必须携带合法管理端 JWT。
     * （路径不一定以 /flow-api 开头，故不能只依赖上文管理端鉴权分支。）
     *
     * @return false 表示已写出拒绝响应
     */
    private boolean assertManagementJwt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // 已在 /flow-api 前缀分支校验过的请求无需重复
        String path = urlPathHelper.getPathWithinApplication(request);
        if (path != null && path.startsWith("/flow-api")) {
            return true;
        }
        String token = JwtTokenUtil.resolveToken(request);
        if (token == null) {
            writeJsonResponse(response, HttpStatus.UNAUTHORIZED.value(),
                    R.fail(ResultCode.TOKEN_EMPTY.getCode(), "token 不能为空！"));
            return false;
        }
        try {
            JwtTokenUtil.validateToken(token);
            return true;
        } catch (ValidateException e) {
            writeJsonResponse(response, HttpStatus.UNAUTHORIZED.value(),
                    R.fail(ResultCode.TOKEN_INVALID.getCode(), "token 已失效！"));
            return false;
        }
    }

    /**
     * @return false 表示已写出拒绝响应，调用方应直接 return
     */
    private boolean assertHostAuthIfRequired(HttpServletRequest request, HttpServletResponse response)
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
        writeJsonResponse(response, e.getHttpStatus(),
                R.failWithErrorCode(e.getHttpStatus(), e.getCode(), e.getMessage()));
        return false;
    }

    /**
     * 第三方开放入口：鉴权后按真实 path 匹配已发布 API 并执行。
     */
    private void handleOpenEntry(HttpServletRequest request, HttpServletResponse response,
                                 String requestPath, String requestMethod) throws IOException {
        boolean openEnabled = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.isOpenEnabled()
                : flowProperties.getOpen() != null && flowProperties.getOpen().isEnabled();
        if (!openEnabled) {
            writeJsonResponse(response, HttpStatus.NOT_FOUND.value(), R.fail(404, "开放入口未启用"));
            return;
        }
        String prefix = normalizeOpenPrefix(resolveOpenEntryPrefix());
        String realPath = requestPath.substring(prefix.length());
        if (StrUtil.isBlank(realPath)) {
            writeJsonResponse(response, HttpStatus.NOT_FOUND.value(),
                    R.fail(404, "开放入口缺少真实接口路径"));
            return;
        }
        if (!realPath.startsWith("/")) {
            realPath = "/" + realPath;
        }
        handleOpenOnRealPath(request, response, realPath, requestMethod, null);
    }

    /**
     * 开放鉴权执行：{@code preMatched} 非空时跳过路由查找（直连真实 path 场景）。
     */
    private void handleOpenOnRealPath(HttpServletRequest request, HttpServletResponse response,
                                      String realPath, String requestMethod,
                                      FlowApiDO preMatched) throws IOException {
        long start = System.currentTimeMillis();
        HttpServletRequest effectiveRequest = wrapOpenBody(request);

        OpenAuthContext authCtx = null;
        FlowApiDO flowApiDO = preMatched;
        MetricsOutcome outcome = MetricsOutcome.FAIL;
        Integer httpStatus = null;
        String errorCode = null;
        try {
            authCtx = openAuthService.authenticate(effectiveRequest, realPath, requestMethod);

            if (flowApiDO == null) {
                flowApiDO = flowApiCacheManager.getExactMatch(requestMethod, realPath);
                if (flowApiDO == null) {
                    FlowApiCacheManager.AntMatchResult matchResult =
                            flowApiCacheManager.getPatternMatch(requestMethod, realPath, ANT_PATH_MATCHER);
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
                writeJsonResponse(response, httpStatus, R.fail(404, "开放接口不存在或未发布"));
                return;
            }

            openAuthService.assertApiGranted(authCtx, flowApiDO.getId(), requestMethod);

            String configMethod = flowApiDO.getMethod();
            if (!requestMethod.equalsIgnoreCase(configMethod)) {
                httpStatus = HttpStatus.METHOD_NOT_ALLOWED.value();
                errorCode = "OPEN_METHOD_NOT_ALLOWED";
                writeJsonResponse(response, httpStatus,
                        R.fail(HttpStatus.METHOD_NOT_ALLOWED.value(),
                                String.format("此接口不支持 %s 请求。请改用 %s 请求。", requestMethod, configMethod)));
                return;
            }

            effectiveRequest.setAttribute("yuOpenPlatformId", authCtx.getPlatformId());
            effectiveRequest.setAttribute("yuOpenAppKey", authCtx.getAppKey());

            // API 维度由 executeApi / 短路径记账；此处仅取业务 outcome 供 PLATFORM
            outcome = executeAndWriteResponse(effectiveRequest, response, flowApiDO);
            httpStatus = response.getStatus() > 0 ? response.getStatus() : HttpStatus.OK.value();
            if (outcome == MetricsOutcome.FAIL) {
                errorCode = "OPEN_BIZ_FAIL";
            }
        } catch (OpenAuthException e) {
            httpStatus = e.getHttpStatus();
            errorCode = e.getCode();
            outcome = MetricsOutcome.AUTH_FAIL;
            if (authCtx == null) {
                authCtx = openAuthService.peekByAppKey(effectiveRequest.getHeader(OpenAuthService.HDR_APP_KEY));
            }
            writeJsonResponse(response, e.getHttpStatus(),
                    R.failWithErrorCode(e.getHttpStatus(), e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("[FlowApiGatewayFilter] 开放入口执行异常:\n{}", ThrowableUtil.getStackTrace(e));
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
            errorCode = "OPEN_INTERNAL_ERROR";
            if (flowApiDO != null) {
                handleExceptionResponse(response, flowApiDO, e);
            } else {
                writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
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

    private HttpServletRequest wrapOpenBody(HttpServletRequest request) {
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

    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            queryParams.put(paramName, request.getParameter(paramName));
        }
        return queryParams;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    private Map<String, Object> toObjectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map) {
            ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private Map<String, Object> extractBodyParams(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null) {
            return new HashMap<>();
        }

        String body = request.getReader().lines().collect(Collectors.joining());
        if (body == null || body.trim().isEmpty()) {
            return new HashMap<>();
        }

        if (contentType.contains("application/x-www-form-urlencoded")) {
            Map<String, Object> params = new HashMap<>();
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                }
            }
            return params;
        } else if (contentType.contains("application/json")) {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        }

        return new HashMap<>();
    }

    private Map<String, Object> mergeParams(Map<String, String> queryParams, Map<String, Object> bodyParams) {
        Map<String, Object> mergedParams = new HashMap<>();
        queryParams.forEach((k, v) -> mergedParams.put("query." + k, v));
        bodyParams.forEach((k, v) -> mergedParams.put("body." + k, v));

        Stream.concat(queryParams.keySet().stream(), bodyParams.keySet().stream())
                .distinct()
                .forEach(k -> mergedParams.put(k, bodyParams.getOrDefault(k, queryParams.get(k))));

        return mergedParams;
    }

    private Pageable extractPageable(HttpServletRequest request) {
        int page = 0;
        int size = 10;
        Sort sort = Sort.unsorted();

        String pageStr = request.getParameter("page");
        String sizeStr = request.getParameter("size");
        String sortStr = request.getParameter("sort");

        try {
            if (pageStr != null) {
                page = Integer.parseInt(pageStr);
            }
            if (sizeStr != null) {
                size = Integer.parseInt(sizeStr);
            }
            if (sortStr != null) {
                sort = parseSortParameter(sortStr);
            }
        } catch (NumberFormatException e) {
        }
        return PageRequest.of(page, size, sort);
    }

    private Sort parseSortParameter(String sortStr) {
        List<Sort.Order> orders = new ArrayList<>();
        for (String param : sortStr.split(",")) {
            String[] parts = param.split(":");
            if (parts.length == 2) {
                orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]));
            }
        }
        return Sort.by(orders);
    }
}
