package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.validator.ParamValidator;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.RequestStep;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Request 请求节点执行器
 *
 * 将输入参数拆分为 headers、params、body 三部分存入上下文
 * 下游节点通过 edge port 选择使用哪部分数据:
 *   - $.{requestNodeId}.headers
 *   - $.{requestNodeId}.params
 *   - $.{requestNodeId}.body
 *
 * 数据来源优先级：
 *   1. vars["request"] 子映射（调试模式下由 FlowApiController.debugRun 预注入）
 *   2. vars 顶层直接的 headers/params/body（正式运行时由 Spring MVC 解析 HTTP 请求后注入）
 *
 * 支持参数校验 (复用 ParamValidator)
 */
public class RequestStepExecutor extends AbstractStepExecutor<RequestStep> {

    @Override
    @SuppressWarnings("unchecked")
    public String execute(RequestStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> vars = context.getVar();

        // ── 1. 确定数据来源 ─────────────────────────────────────────────
        // 调试模式：Controller 将 { headers, params, body } 打包在 vars["request"] 下；
        // 正式运行：Spring MVC 解析 HTTP 请求后，headers/params/body 直接在 vars 顶层。
        Map<String, Object> source = vars;
        Object requestEntry = vars.get("request");
        if (requestEntry instanceof Map) {
            // 调试模式：优先使用 vars["request"] 子映射
            source = (Map<String, Object>) requestEntry;
        }

        // ── 2. 提取 headers / params / body ──────────────────────────────
        Map<String, Object> headers = extractMap(source, "headers");
        Map<String, Object> params  = extractMap(source, "params");
        Map<String, Object> body    = extractMap(source, "body");

        // ── 3. 收集所有参数用于校验 (params + body 合并) ─────────────────
        Map<String, Object> allParams = new HashMap<>();
        allParams.putAll(params);
        allParams.putAll(body);

        // ── 4. 参数校验 ───────────────────────────────────────────────────
        ParamValidator.validate(step.getValidations(), allParams);

        // ── 5. 存入上下文（以节点 ID 为 key，供下游 $.{nodeId}.params 引用）──
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("headers", headers);
        requestData.put("params", params);
        requestData.put("body", body);
        context.setVar(step.getId(), requestData);

        // Request 节点有多个输出端口 (headers / params / body)
        // FlowEngine 会根据 edge 中的 port 来路由，优先返回已配置的 port
        for (String port : new String[]{"headers", "params", "body"}) {
            if (step.getNext().containsKey(port)) {
                return port;
            }
        }

        // 如果 next 中有 "out" 等通用端口，也支持
        if (step.getNext().containsKey(PortNames.OUT)) {
            return PortNames.OUT;
        }

        return "body"; // 默认返回 body
    }

    /**
     * 从指定 Map 中提取 Map 类型数据，如果不是 Map 则返回空 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }
}
