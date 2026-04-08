package org.yu.flow.engine.evaluator.executor;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.ResponseResult;
import org.yu.flow.engine.model.step.ResponseStep;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Response HTTP 响应终态节点执行器
 *
 * 主要职责：
 * 1. 构建局部上下文 (Local Context)
 * 2. 模版渲染 (Template Rendering) status, headers 和 body
 * 3. 构造 ResponseResult 对象。
 *
 * 【关于如何绕过统一拦截器及处理 HTTP Header 的机制】
 * 此执行器本身不直接参与构建原生的 HttpServletResponse，而是产生出一个带特殊印记的 ResponseResult 对象（装载有 HTTP Status Code、自定Headers 以及纯正数据体 Body）；
 * 当 FlowEngine 在 execute 方法结尾处一旦探测到该执行流结束时的最终产物是 ResponseResult，
 * 它便触发拦截并接管行为：将原本包裹的 ExecutionResult/R<T> 层弃用，进而直接实例化为一个带全部对应头信息(Headers)的 org.springframework.http.ResponseEntity 返回；
 * 根据 Spring Boot 处理流程，当 Controller 的方法（需返回类型兼容）向外掷出 ResponseEntity 这个原生 HTTP 实体类型时，
 * 默认的 @RestControllerAdvice / ResponseBodyAdvice 等统一的外壳包装会被自动越狱（跳过）。
 * 这样就可以把纯粹直接的 JSON / Text 及完全自定义的状态码毫发无损地回送给来访端，满足类似于 Webhook 的特殊对接需要！
 */
@Slf4j
public class ResponseStepExecutor extends AbstractStepExecutor<ResponseStep> {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.+?)}");

    @Override
    public String execute(ResponseStep step, ExecutionContext context, FlowDefinition flow) {
        // 1. 准备局部上下文 (复用父类的 prepareInputs 方法)
        Map<String, Object> localVariables = this.prepareInputs(step, context, flow);

        // 2. 解析 status (模版渲染)
        int statusCode = resolveStatus(step.getStatus(), localVariables);

        // 3. 解析 headers (模版渲染)
        Map<String, String> headers = new HashMap<>();
        if (step.getHeaders() != null) {
            step.getHeaders().forEach((k, v) -> headers.put(k, resolveString(v, localVariables)));
        }

        // 4. 解析 body (模版渲染)
        Object body = resolveBody(step.getBody(), localVariables);

        // 5. 构造包含解析后最终结果对象（ResponseResult 包装类）
        ResponseResult responseResult = new ResponseResult(statusCode, headers, body);

        log.info("Response [{}]: status={}, headers={}, body={}", step.getId(), statusCode, headers, body);

        // 6. 存入上下文，标记为流程最终响应
        context.setVar(step.getId(), responseResult);
        context.setOutput(responseResult);

        // 返回 null，结束当前执行流
        return null;
    }


    private int resolveStatus(Object status, Map<String, Object> localVariables) {
        if (status instanceof Integer) {
            return (Integer) status;
        }
        if (status instanceof String) {
            String str = ((String) status).trim();
            // 尝试 ${} 变量引用
            Matcher matcher = VAR_PATTERN.matcher(str);
            if (matcher.matches()) {
                Object value = getValueByPath(matcher.group(1), localVariables);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return 200;
            }
        }
        return 200;
    }

    @SuppressWarnings("unchecked")
    private Object resolveBody(Object body, Map<String, Object> localVariables) {
        if (body == null) return null;

        if (body instanceof String) {
            String str = ((String) body).trim();
            // 完全匹配 ${...}：直接返回路径对应的原始对象（可能是 Map/List 等）
            Matcher matcher = VAR_PATTERN.matcher(str);
            if (matcher.matches()) {
                return getValueByPath(matcher.group(1), localVariables);
            }
            // 含有 ${...} 片段：按字符串模板展开
            if (str.contains("${")) {
                return resolveString(str, localVariables);
            }
            return str;
        }

        // 如果 body 是 Map，递归解析其中的 ${} 引用
        if (body instanceof Map) {
            Map<String, Object> bodyMap = (Map<String, Object>) body;
            Map<String, Object> resolved = new HashMap<>();
            bodyMap.forEach((k, v) -> {
                if (v instanceof String) {
                    String str = ((String) v).trim();
                    Matcher matcher = VAR_PATTERN.matcher(str);
                    if (matcher.matches()) {
                        resolved.put(k, getValueByPath(matcher.group(1), localVariables));
                    } else if (str.contains("${")) {
                        resolved.put(k, resolveString(str, localVariables));
                    } else {
                        resolved.put(k, str);
                    }
                } else {
                    resolved.put(k, v);
                }
            });
            return resolved;
        }

        return body;
    }

    private String resolveString(String template, Map<String, Object> localVariables) {
        if (template == null) return null;
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            Object value = getValueByPath(matcher.group(1), localVariables);
            matcher.appendReplacement(sb, value != null ? Matcher.quoteReplacement(String.valueOf(value)) : "");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Object getValueByPath(String path, Map<String, Object> localVariables) {
        if (localVariables == null || localVariables.isEmpty()) {
            return null;
        }
        try {
            // 支持 localVariables 直接提取
            String jsonPath = path.startsWith("$.") ? path : "$." + path;
            return JsonPath.read(localVariables, jsonPath);
        } catch (PathNotFoundException | IllegalArgumentException e) {
            return null;
        }
    }
}
