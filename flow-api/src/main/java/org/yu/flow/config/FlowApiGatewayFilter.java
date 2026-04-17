package org.yu.flow.config;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
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
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.util.FlowObjectMapperUtil;
import org.yu.flow.util.ThrowableUtil;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    private final YuFlowProperties flowProperties;
    private final FlowApiExecutionService flowApiService;
    private final FlowApiCacheManager flowApiCacheManager;
    private final SchemaValidatorService schemaValidatorService;
    private final ResponseStrategyResolver responseStrategyResolver;
    private final ResponseTransformer responseTransformer;

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();
    private final UrlPathHelper urlPathHelper = createUrlPathHelper();

    public FlowApiGatewayFilter(YuFlowProperties flowProperties,
                                FlowApiExecutionService flowApiService,
                                FlowApiCacheManager flowApiCacheManager,
                                SchemaValidatorService schemaValidatorService,
                                ResponseStrategyResolver responseStrategyResolver,
                                ResponseTransformer responseTransformer) {
        this.flowProperties = flowProperties;
        this.flowApiService = flowApiService;
        this.flowApiCacheManager = flowApiCacheManager;
        this.schemaValidatorService = schemaValidatorService;
        this.responseStrategyResolver = responseStrategyResolver;
        this.responseTransformer = responseTransformer;
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

        // 3. 管理端鉴权
        if (requestPath.startsWith("/flow-api")) {
            if (requestPath.startsWith("/flow-api/login")) {
                filterChain.doFilter(request, response);
                return;
            }
            String token = JwtTokenUtil.resolveToken(request);
            if (token == null) {
                writeJsonResponse(response, HttpStatus.OK.value(), R.fail("token 不能为空！"));
                return;
            }
            try {
                JwtTokenUtil.validateToken(token);
            } catch (ValidateException e) {
                writeJsonResponse(response, HttpStatus.OK.value(),
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

            // 5. 安全级别旁路判断
            if (shouldBypassBySecurityLevel(request, flowApiDO)) {
                filterChain.doFilter(request, response);
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
                executeAndWriteResponse(request, response, flowApiDO);
            } catch (Exception e) {
                log.error("[FlowApiGatewayFilter] API 业务执行异常:\n{}", ThrowableUtil.getStackTrace(e));
                handleExceptionResponse(response, flowApiDO, e);
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

    private void executeAndWriteResponse(HttpServletRequest request, HttpServletResponse response,
                                         FlowApiDO flowApiDO) throws Exception {
        // 1. 提取所有请求参数
        Map<String, String> queryParams = extractQueryParams(request);
        Map<String, Object> bodyParams = extractBodyParams(request);
        Map<String, String> headers = extractHeaders(request);

        Map<String, Object> inputParamsMap = new HashMap<>(8);
        inputParamsMap.put("@QP", queryParams);
        inputParamsMap.put("@BP", bodyParams);
        inputParamsMap.put("@PP", request.getAttribute("flowPathVariables"));
        // 兼容 Request 节点
        inputParamsMap.put("headers", headers);
        inputParamsMap.put("params", queryParams);
        inputParamsMap.put("body", bodyParams);

        // 2. 合并参数（body 优先）
        Map<String, Object> mergeParamsMap = mergeParams(queryParams, bodyParams);

        // 3. 提取分页对象
        Pageable pageable = extractPageable(request);

        // 3.5 JSON Schema 入参前置校验
        String contractRule = flowApiDO.getContract();
        if (StrUtil.isNotBlank(contractRule)) {
            try {
                schemaValidatorService.validateFromContract(contractRule, bodyParams, queryParams);
            } catch (SchemaValidationException e) {
                writeJsonResponse(response, HttpStatus.BAD_REQUEST.value(),
                        R.fail(400, e.getMessage()));
                return;
            }
        }

        // 4. 执行 API
        Object result = flowApiService.executeApi(flowApiDO, inputParamsMap, pageable, response);

        // 5. 获取包装上下文
        ResponseWrapperContext context = responseStrategyResolver.resolve(flowApiDO);

        // 6. 写出响应
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpStatus.OK.value());
        response.setHeader("ss-flow", "yes");

        if (result instanceof ResponseEntity) {
            writeResponseEntity(response, (ResponseEntity<?>) result);
        } else {
            String templateToUse;
            if (result instanceof R && !((R) result).getOk()) {
                templateToUse = context.getFailWrapper();
            } else if (isPageResponse(result) || "PAGE".equalsIgnoreCase(flowApiDO.getResponseType())) {
                templateToUse = context.getPageWrapper();
            } else {
                templateToUse = context.getSuccessWrapper();
            }

            Object finalResult = responseTransformer.transform(result, templateToUse);
            objectMapper.writeValue(response.getWriter(), finalResult);
        }
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
