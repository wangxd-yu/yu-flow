package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.expression.ExpressionEvaluatorFactory;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.HttpRequestStep;
import org.yu.flow.log.third.domain.FlowThirdLogDO;
import org.yu.flow.log.third.support.ThirdLogRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HttpRequest 节点执行器
 * 负责执行 HTTP 请求，支持变量替换、超时控制和结果回写；
 * 节点 logEnabled 开启时异步写入三方调用日志。
 */
public class HttpRequestStepExecutor extends AbstractStepExecutor<HttpRequestStep> {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestStepExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 正则匹配 ${varName}
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    // 全局 OkHttpClient (连接池复用)
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build();

    /** 忽略 SSL 校验的共享 TrustManager（仅 ignoreSsl=true 时使用） */
    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static final SSLSocketFactory TRUST_ALL_SOCKET_FACTORY;

    static {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{TRUST_ALL_MANAGER}, new SecureRandom());
            TRUST_ALL_SOCKET_FACTORY = sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public String execute(HttpRequestStep step, ExecutionContext context, FlowDefinition flow) {
        long startTime = System.currentTimeMillis();
        FlowThirdLogDO logDO = null;
        boolean logging = isLogEnabled(step);
        try {
            // 1. 准备输入变量
            Map<String, Object> inputs = this.prepareInputs(step, context, flow);

            // 2. 构建 HttpUrl
            HttpUrl finalUrl = buildUrl(step, inputs);

            // 3. 构建 Request
            Request request = buildRequest(step, finalUrl, inputs);

            if (logging) {
                logDO = buildBaseLog(step, context, request, inputs);
            }

            boolean ignoreSsl = resolveIgnoreSsl(step);
            if (ignoreSsl) {
                log.info("HttpRequest [{}] ignoreSsl=true，跳过 SSL 证书校验 → {}", step.getId(), finalUrl);
            }

            // 4. 定制超时 / SSL Client
            OkHttpClient stepClient = buildClient(step, ignoreSsl);

            // 5. 执行请求并处理响应；证书失败时自动 insecure 重试一次
            try {
                return executeRequestAndParseResponse(step, request, stepClient, startTime, context, logDO);
            } catch (Exception first) {
                if (!ignoreSsl && isCertificateProblem(first)) {
                    log.warn("HttpRequest [{}] SSL 证书校验失败，自动忽略证书重试。建议在节点开启「忽略SSL」。err={}",
                            step.getId(), first.getMessage());
                    OkHttpClient insecureClient = buildClient(step, true);
                    return executeRequestAndParseResponse(step, request, insecureClient, startTime, context, logDO);
                }
                throw first;
            }

        } catch (Exception e) {
            log.error("HTTP Request failed: {}", e.getMessage(), e);
            // 异常结果回写
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", -1);
            errorResult.put("error", e.getMessage());
            errorResult.put("timeMs", System.currentTimeMillis() - startTime);
            context.setVar(step.getId(), errorResult);

            if (logging && logDO != null) {
                logDO.setIsSuccess(0);
                logDO.setErrorMessage(truncate(e.getMessage(), 1000));
                logDO.setResponseStatus(-1);
            } else if (logging) {
                // URL/请求构建阶段失败，尽量补一条基础日志
                logDO = FlowThirdLogDO.builder()
                        .apiType(resolveApiType(step))
                        .source(resolveSource(context))
                        .sourceRef(context.getSourceRef())
                        .sourceName(context.getSourceName())
                        .requestUrl(step.getUrl())
                        .requestMethod(step.getMethod() != null ? step.getMethod().toUpperCase() : "GET")
                        .isSuccess(0)
                        .errorMessage(truncate(e.getMessage(), 1000))
                        .responseStatus(-1)
                        .build();
            }
            return PortNames.FAIL;
        } finally {
            if (logging && logDO != null) {
                logDO.setElapsedTime(System.currentTimeMillis() - startTime);
                ThirdLogRecorder.saveAsync(logDO);
            }
        }
    }

    /**
     * 解析 ignoreSsl。
     * <ul>
     *   <li>null / 未配置 → true（兼容自签名内网 HTTPS，与常见集成任务一致）</li>
     *   <li>显式 false → 严格校验证书</li>
     * </ul>
     */
    private static boolean resolveIgnoreSsl(HttpRequestStep step) {
        Object raw = step.getIgnoreSsl();
        if (raw == null) {
            return true;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) {
            return false;
        }
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    private static boolean isCertificateProblem(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof javax.net.ssl.SSLException
                    || cur instanceof java.security.cert.CertificateException) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null && (msg.contains("PKIX")
                    || msg.contains("certificate_unknown")
                    || msg.contains("unable to find valid certification path")
                    || msg.contains("SSLHandshakeException"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private boolean isLogEnabled(HttpRequestStep step) {
        return step.getLogEnabled() == null || Boolean.TRUE.equals(step.getLogEnabled());
    }

    private String resolveApiType(HttpRequestStep step) {
        if (step.getApiType() != null && !step.getApiType().isBlank()) {
            return step.getApiType().trim();
        }
        return step.getId();
    }

    private String resolveSource(ExecutionContext context) {
        String source = context.getInvokeSource();
        if (source == null || source.isBlank()) {
            return "OTHER";
        }
        return source;
    }

    private FlowThirdLogDO buildBaseLog(HttpRequestStep step, ExecutionContext context,
                                        Request request, Map<String, Object> inputs) {
        Map<String, String> headers = resolveMap(step.getHeaders(), inputs);
        Map<String, String> params = resolveMap(step.getParams(), inputs);
        String bodyStr = extractBodyString(step, inputs);

        Map<String, Object> paramsPayload = new HashMap<>();
        if (params != null && !params.isEmpty()) {
            paramsPayload.put("query", params);
        }
        if (bodyStr != null && !bodyStr.isEmpty()) {
            paramsPayload.put("body", bodyStr);
        }

        String requestParamsJson = null;
        String requestHeadersJson = null;
        try {
            if (!paramsPayload.isEmpty()) {
                requestParamsJson = objectMapper.writeValueAsString(paramsPayload);
            }
            if (headers != null && !headers.isEmpty()) {
                requestHeadersJson = objectMapper.writeValueAsString(headers);
            }
        } catch (Exception e) {
            log.warn("序列化三方日志请求参数失败: {}", e.getMessage());
        }

        String curl = buildCurl(request.method(), request.url().toString(), headers, bodyStr);

        return FlowThirdLogDO.builder()
                .apiType(resolveApiType(step))
                .source(resolveSource(context))
                .sourceRef(context.getSourceRef())
                .sourceName(context.getSourceName())
                .requestUrl(request.url().toString())
                .requestMethod(request.method())
                .requestParams(requestParamsJson)
                .requestHeaders(requestHeadersJson)
                .curl(curl)
                .build();
    }

    private String extractBodyString(HttpRequestStep step, Map<String, Object> inputs) {
        if (!requiresBody(step.getMethod())) {
            return null;
        }
        Object body = step.getBody();
        if (body == null) {
            return "";
        }
        try {
            if (body instanceof String) {
                return resolveString((String) body, inputs);
            }
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }

    /**
     * 参照 GatewayService 风格拼装 curl。
     */
    private String buildCurl(String method, String url, Map<String, String> headers, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X ").append(method != null ? method.toUpperCase() : "GET");
        sb.append(" '").append(escapeSingleQuotes(url)).append("'");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                sb.append(" -H '")
                        .append(escapeSingleQuotes(entry.getKey()))
                        .append(": ")
                        .append(escapeSingleQuotes(entry.getValue() != null ? entry.getValue() : ""))
                        .append("'");
            }
        }
        if (body != null && !body.isEmpty() && requiresBody(method)) {
            sb.append(" -d '").append(escapeSingleQuotes(body)).append("'");
        }
        return sb.toString();
    }

    private static String escapeSingleQuotes(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "'\\''");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private HttpUrl buildUrl(HttpRequestStep step, Map<String, Object> inputs) {
        String url = resolveString(step.getUrl(), inputs);
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }

        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }

        HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
        Map<String, String> resolvedParams = resolveMap(step.getParams(), inputs);
        if (resolvedParams != null) {
            resolvedParams.forEach(urlBuilder::addQueryParameter);
        }
        return urlBuilder.build();
    }

    private Request buildRequest(HttpRequestStep step, HttpUrl finalUrl, Map<String, Object> inputs) throws Exception {
        Map<String, String> resolvedHeaders = resolveMap(step.getHeaders(), inputs);
        RequestBody requestBody = null;

        if (requiresBody(step.getMethod())) {
            String bodyStr = "";
            String contentType = resolvedHeaders.getOrDefault("Content-Type", "application/json; charset=utf-8");
            MediaType mediaType = MediaType.parse(contentType);

            Object body = step.getBody();
            if (body != null) {
                if (body instanceof String) {
                    bodyStr = resolveString((String) body, inputs);
                } else {
                    bodyStr = objectMapper.writeValueAsString(body);
                }
            }
            requestBody = RequestBody.create(bodyStr, mediaType);
        } else if ("POST".equalsIgnoreCase(step.getMethod()) || "PUT".equalsIgnoreCase(step.getMethod())) {
            requestBody = RequestBody.create("", null);
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(finalUrl)
                .method(step.getMethod().toUpperCase(), requestBody);

        if (resolvedHeaders != null) {
            resolvedHeaders.forEach(requestBuilder::header);
        }
        return requestBuilder.build();
    }

    private OkHttpClient buildClient(HttpRequestStep step, boolean ignoreSsl) {
        int timeoutMs = step.getTimeout() > 0 ? step.getTimeout() : 30000;
        // ignoreSsl 时从干净 Builder 构建，避免复用全局 client 的默认 TrustManager
        OkHttpClient.Builder builder = ignoreSsl
                ? new OkHttpClient.Builder()
                : client.newBuilder();

        builder.readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(Math.min(timeoutMs, 10000), TimeUnit.MILLISECONDS);

        if (ignoreSsl) {
            builder.sslSocketFactory(TRUST_ALL_SOCKET_FACTORY, TRUST_ALL_MANAGER)
                    .hostnameVerifier((hostname, session) -> true);
        }
        return builder.build();
    }

    private String executeRequestAndParseResponse(HttpRequestStep step, Request request, OkHttpClient stepClient,
                                                  long startTime, ExecutionContext context,
                                                  FlowThirdLogDO logDO) throws Exception {
        try (Response response = stepClient.newCall(request).execute()) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> result = new HashMap<>();
            result.put("status", response.code());

            Map<String, String> respHeaders = new HashMap<>();
            response.headers().forEach(pair -> respHeaders.put(pair.getFirst(), pair.getSecond()));
            result.put("headers", respHeaders);

            String respBodyStr = response.body() != null ? response.body().string() : "";
            try {
                if (respBodyStr.trim().startsWith("{") || respBodyStr.trim().startsWith("[")) {
                    result.put("body", objectMapper.readValue(respBodyStr, Object.class));
                } else {
                    result.put("body", respBodyStr);
                }
            } catch (Exception e) {
                result.put("body", respBodyStr);
            }

            result.put("timeMs", duration);
            context.setVar(step.getId(), result);

            boolean success = resolveSuccess(step, result, response.isSuccessful());

            if (logDO != null) {
                logDO.setResponseStatus(response.code());
                logDO.setResponseBody(respBodyStr);
                logDO.setIsSuccess(success ? 1 : 0);
                if (!success) {
                    logDO.setErrorMessage("HTTP " + response.code());
                }
            }

            return success ? PortNames.SUCCESS : PortNames.FAIL;
        }
    }

    /**
     * 有 successCondition 时按 Aviator 表达式判断；否则沿用 HTTP 2xx。
     * 表达式可引用：status、body、headers、timeMs
     */
    private boolean resolveSuccess(HttpRequestStep step, Map<String, Object> result, boolean httpOk) {
        String condition = step.getSuccessCondition();
        if (condition == null || condition.trim().isEmpty()) {
            return httpOk;
        }
        try {
            return ExpressionEvaluatorFactory.getAviator().evaluateBoolean(condition.trim(), result);
        } catch (Exception e) {
            log.warn("successCondition 求值失败，视为 fail。expr={}, err={}", condition, e.getMessage());
            return false;
        }
    }

    private boolean requiresBody(String method) {
        // GET, DELETE, HEAD usually don't have body standardly, but some APIs might allow.
        // For safety, follow standard: POST, PUT, PATCH use body.
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
    }

    // 变量替换辅助方法
    private String resolveString(String template, Map<String, Object> inputs) {
        if (template == null) return null;
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object val = inputs.get(varName);
            // 如果变量不存在，替换为空字符串或保留原样？这里替换为空字符串以避免 format error
            String replacement = val != null ? val.toString() : "";
            // escape $ and \
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Map<String, String> resolveMap(Map<String, String> map, Map<String, Object> inputs) {
        if (map == null) return new HashMap<>();
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            resolved.put(resolveString(entry.getKey(), inputs), resolveString(entry.getValue(), inputs));
        }
        return resolved;
    }
}
