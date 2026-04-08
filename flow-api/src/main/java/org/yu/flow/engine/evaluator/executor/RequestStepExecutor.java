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
 *   - $.request.headers
 *   - $.request.params
 *   - $.request.body
 *
 * 支持参数校验 (复用 ParamValidator)
 */
public class RequestStepExecutor extends AbstractStepExecutor<RequestStep> {

    @Override
    @SuppressWarnings("unchecked")
    public String execute(RequestStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> vars = context.getVar();

        // 1. 提取 headers / params / body
        Map<String, Object> headers = extractMap(vars, "headers");
        Map<String, Object> params  = extractMap(vars, "params");
        Map<String, Object> body    = extractMap(vars, "body");

        // 2. 收集所有参数用于校验 (params + body 合并)
        Map<String, Object> allParams = new HashMap<>();
        allParams.putAll(params);
        allParams.putAll(body);

        // 3. 参数校验
        ParamValidator.validate(step.getValidations(), allParams);

        // 4. 存入上下文
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("headers", headers);
        requestData.put("params", params);
        requestData.put("body", body);
        context.setVar(step.getId(), requestData);

        // Request 节点有多个输出端口 (headers / params / body)
        // 但流程连线时通过 edge 的 port 来区分，这里统一返回 null
        // FlowEngine 会根据 next 映射中有哪个 port 来路由
        // 如果只有一个下游，通常用 "body" 或 "params"
        // 返回第一个有效的 port
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
     * 从上下文变量中提取 Map 类型数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> vars, String key) {
        Object value = vars.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }
}
