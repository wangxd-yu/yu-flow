package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.HttpRequestStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HttpRequest 节点执行器
 * 负责执行 HTTP 请求，支持变量替换、超时控制和结果回写
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

    @Override
    public String execute(HttpRequestStep step, ExecutionContext context, FlowDefinition flow) {
        long startTime = System.currentTimeMillis();
        try {
            // 1. 准备输入变量
            Map<String, Object> inputs = this.prepareInputs(step, context, flow);

            // 2. 构建 HttpUrl
            HttpUrl finalUrl = buildUrl(step, inputs);

            // 3. 构建 Request
            Request request = buildRequest(step, finalUrl, inputs);

            // 4. 定制超时 Client
            OkHttpClient stepClient = buildClient(step);

            // 5. 执行请求并处理响应
            return executeRequestAndParseResponse(step, request, stepClient, startTime, context);

        } catch (Exception e) {
            log.error("HTTP Request failed: {}", e.getMessage(), e);
            // 异常结果回写
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", -1);
            errorResult.put("error", e.getMessage());
            errorResult.put("timeMs", System.currentTimeMillis() - startTime);
            context.setVar(step.getId(), errorResult);
            return PortNames.FAIL;
        }
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

    private OkHttpClient buildClient(HttpRequestStep step) {
        if (step.getTimeout() > 0 && step.getTimeout() != 30000) {
            return client.newBuilder()
                    .readTimeout(step.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(step.getTimeout(), TimeUnit.MILLISECONDS)
                    .connectTimeout(Math.min(step.getTimeout(), 10000), TimeUnit.MILLISECONDS)
                    .build();
        }
        return client;
    }

    private String executeRequestAndParseResponse(HttpRequestStep step, Request request, OkHttpClient stepClient,
                                                  long startTime, ExecutionContext context) throws Exception {
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

            return response.isSuccessful() ? PortNames.SUCCESS : PortNames.FAIL;
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
