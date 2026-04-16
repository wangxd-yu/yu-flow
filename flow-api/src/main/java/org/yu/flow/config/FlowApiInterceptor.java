package org.yu.flow.config;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.config.response.ResponseWrapperContext;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.dto.R;
import org.yu.flow.dto.ResultCode;
import org.yu.flow.exception.SchemaValidationException;
import org.yu.flow.util.FlowObjectMapperUtil;
import org.yu.flow.util.JsonDeserializerUtil;
import org.yu.flow.util.ThrowableUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 动态 API 请求拦截器
 *
 * <p>拦截所有 HTTP 请求，依次进行：
 * <ol>
 *   <li>管理端（{@code /flow-api}）JWT 鉴权</li>
 *   <li>通过 {@link FlowApiCacheManager} 在纯内存中匹配动态路由（精确 O(1) → Ant 模式 O(N)）</li>
 *   <li>提取请求参数并委托 {@link FlowApiExecutionService#executeApi} 执行业务</li>
 *   <li>写出响应（支持 ResponseEntity、JSON 包装、纯文本等多格式）</li>
 * </ol>
 *
 * @author yu-flow
 */
@Slf4j
@Component
public class FlowApiInterceptor implements HandlerInterceptor {

    /** 接口安全级别请求头标识 */
    private static final String SECURITY_LEVEL_HEADER = "ss-level";

    /** JSON 响应 Content-Type 常量 */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    /** Ant 风格路径匹配器（线程安全，支持 /user/{id} 等路径参数） */
    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    @Resource
    private YuFlowProperties flowProperties;

    @Resource
    private FlowApiExecutionService flowApiService;

    @Resource
    private FlowApiCacheManager flowApiCacheManager;

    @Resource
    private SchemaValidatorService schemaValidatorService;

    @Resource
    private org.yu.flow.config.response.ResponseStrategyResolver responseStrategyResolver;

    @Resource
    private org.yu.flow.config.response.ResponseTransformer responseTransformer;

    /** 全局复用的 ObjectMapper（线程安全），杜绝方法内重复 new */
    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();

    /** 全局复用的路径辅助工具，避免每次请求实例化 */
    private final UrlPathHelper urlPathHelper = createUrlPathHelper();

    private UrlPathHelper createUrlPathHelper() {
        UrlPathHelper helper = new UrlPathHelper();
        helper.setAlwaysUseFullPath(false);
        helper.setUrlDecode(false);
        return helper;
    }

    // ============================= 拦截入口 =============================

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 全局开关关闭，直接放行
        if (!flowProperties.isEnabled()) {
            return true;
        }

        String requestPath = urlPathHelper.getPathWithinApplication(request);
        String requestMethod = request.getMethod();

        // ─── 管理端鉴权 ───
        if (requestPath.startsWith("/flow-api")) {
            if (requestPath.startsWith("/flow-api/login")) {
                return true;
            }
            String token = JwtTokenUtil.resolveToken(request);
            if (token == null) {
                writeJsonResponse(response, HttpStatus.OK.value(), R.fail("token 不能为空！"));
                return false;
            }
            try {
                JwtTokenUtil.validateToken(token);
            } catch (ValidateException e) {
                writeJsonResponse(response, HttpStatus.OK.value(),
                        R.fail(ResultCode.TOKEN_INVALID.getCode(), "token 已失效！"));
                return false;
            }
        }

        // ─── 路由匹配（纯内存，零网络 I/O） ───

        // 1. 精确匹配 O(1)
        FlowApiDO flowApiDO = flowApiCacheManager.getExactMatch(requestMethod, requestPath);

        // 2. 精确未命中 → Ant 模式匹配 O(N)
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

        // 未匹配到任何动态路由，放行给 Spring MVC 继续处理
        if (flowApiDO == null) {
            return true;
        }

        // ─── 安全级别旁路判断 ───
        if (shouldBypassBySecurityLevel(request, flowApiDO)) {
            return true;
        }

        // ─── 请求方法校验 ───
        String configMethod = flowApiDO.getMethod();
        if (!requestMethod.equalsIgnoreCase(configMethod)) {
            writeJsonResponse(response, HttpStatus.METHOD_NOT_ALLOWED.value(),
                    R.fail(HttpStatus.METHOD_NOT_ALLOWED.value(),
                            String.format("此接口不支持 %s 请求。请改用 %s 请求。", requestMethod, configMethod)));
            return false;
        }

        // ─── 执行业务逻辑 ───
        try {
            return executeAndWriteResponse(request, response, flowApiDO);
        } catch (Exception e) {
            log.error(ThrowableUtil.getStackTrace(e));
            handleExceptionResponse(response, flowApiDO, e);
            return false;
        }
    }

    private void handleExceptionResponse(HttpServletResponse response, FlowApiDO flowApiDO, Exception e) throws IOException {
        R<Object> r = R.fail(500, e.getMessage() != null ? e.getMessage() : "系统内部错误，请联系管理员");
        org.yu.flow.config.response.ResponseWrapperContext context = responseStrategyResolver.resolve(flowApiDO);
        Object finalResult = responseTransformer.transform(r, context.getFailWrapper());
        writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), finalResult);
    }

    // ============================= 业务执行与响应写出 =============================

    /**
     * 执行动态 API 业务逻辑并写出响应。
     *
     * @return 始终返回 false（拦截请求，不再向下传递）
     */
    private boolean executeAndWriteResponse(HttpServletRequest request, HttpServletResponse response,
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

        // 3.5 JSON Schema 入参前置校验（同时校验 Body 和 Query Params）
        String contractRule = flowApiDO.getContract();
        if (StrUtil.isNotBlank(contractRule)) {
            try {
                schemaValidatorService.validateFromContract(contractRule, bodyParams, queryParams);
            } catch (SchemaValidationException e) {
                writeJsonResponse(response, HttpStatus.BAD_REQUEST.value(),
                        R.fail(400, e.getMessage()));
                return false;
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
            // 兜底：如果执行结果本身没有外层 R 处理，但模板可能需要 code / message 等结构
            // 如果觉得在这里统一包一层 Map 再 transform 更安全，也可以。
            // 这里我们直接将原结果传入 transformer（如果是 R/Page，内部有相应字段）
            Object finalResult = responseTransformer.transform(result, templateToUse);
            objectMapper.writeValue(response.getWriter(), finalResult);
        }

        return false;
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
     * 处理 ResponseEntity 类型的返回值：覆盖状态码、响应头，并按 body 类型写出。
     */
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

    // ============================= 统一响应写出 =============================

    /**
     * 统一的 JSON 响应写出方法，消除重复的 response 设置样板代码。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码
     * @param data     响应体对象（将通过 ObjectMapper 序列化为 JSON）
     */
    private void writeJsonResponse(HttpServletResponse response, int status, Object data) throws IOException {
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status);
        // 使用流式写出，避免 writeValueAsString 产生大字符串导致的 GC 压力
        objectMapper.writeValue(response.getWriter(), data);
    }

    // ============================= 安全级别判断 =============================

    /**
     * 判断是否应跳过动态 API 路由，使用代码中定义的原生接口。
     *
     * <p>比较请求头 {@code ss-level} 与数据库中配置的接口安全级别，
     * 若请求级别更高（值更大），则跳过动态路由，放行给 Spring MVC 原生 Controller 处理。</p>
     *
     * @param request   HTTP 请求
     * @param flowApiDO 匹配到的动态 API 定义
     * @return true 表示应跳过（放行），false 表示正常走动态 API
     */
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

    // ============================= 参数提取 =============================

    /**
     * 提取 URL 查询参数（Query String）。
     */
    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            queryParams.put(paramName, request.getParameter(paramName));
        }
        return queryParams;
    }

    /**
     * 提取请求头。
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    /**
     * 提取请求体参数。
     *
     * <p>支持 {@code application/x-www-form-urlencoded} 和 {@code application/json} 两种格式。
     * 复用类级别的 {@link #objectMapper}，避免每次请求创建新实例的性能损耗。</p>
     *
     * @param request HTTP 请求
     * @return 请求体参数 Map；无可用数据时返回空 Map
     * @throws IOException 读取请求体时发生 I/O 异常
     */
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

    // ============================= 参数合并 =============================

    /**
     * 合并查询参数和请求体参数。
     *
     * <p>同名参数 body 优先覆盖 query，并分别添加 {@code query.} 和 {@code body.} 前缀的带命名空间版本。</p>
     */
    private Map<String, Object> mergeParams(Map<String, String> queryParams, Map<String, Object> bodyParams) {
        Map<String, Object> mergedParams = new HashMap<>();

        queryParams.forEach((k, v) -> mergedParams.put("query." + k, v));
        bodyParams.forEach((k, v) -> mergedParams.put("body." + k, v));

        Stream.concat(queryParams.keySet().stream(), bodyParams.keySet().stream())
                .distinct()
                .forEach(k -> mergedParams.put(k, bodyParams.getOrDefault(k, queryParams.get(k))));

        return mergedParams;
    }



    // ============================= 分页提取 =============================

    /**
     * 从请求参数中提取分页信息。
     *
     * @return 分页对象，默认 page=0, size=10, unsorted
     */
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
            // 格式错误使用默认值
        }

        return PageRequest.of(page, size, sort);
    }

    /**
     * 解析排序参数字符串（格式：{@code field:asc,field2:desc}）。
     */
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
