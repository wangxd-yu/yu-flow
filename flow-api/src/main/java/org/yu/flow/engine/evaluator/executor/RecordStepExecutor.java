package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.RecordStep;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Record 数据构造节点执行器 (Object Builder)
 *
 * 遍历 schema 配置，解析每个 value 表达式，
 * 构建一个新的 Map 对象并存入 context。
 */
@Slf4j
public class RecordStepExecutor extends AbstractStepExecutor<RecordStep> {

    private static final Pattern VAR_PATTERN = Pattern.compile("^\\$\\{(.+)}$");

    @Override
    public String execute(RecordStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> schema = step.getSchema();
        if (schema == null || schema.isEmpty()) {
            log.warn("Record [{}]: schema 为空", step.getId());
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put(ContextKeys.RESULT, new HashMap<>());
            context.setVar(step.getId(), emptyResult);
            return PortNames.OUT;
        }

        Map<String, Object> record = new HashMap<>();
        Map<String, Object> contextData = context.getVar();

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String fieldName = entry.getKey();
            Object valueExpr = entry.getValue();

            Object resolvedValue = null;
            if (valueExpr instanceof String) {
                resolvedValue = resolveExpression(valueExpr, contextData, context);
            } else if (valueExpr instanceof Map) {
                // Handle complex object config (e.g. {"extractPath": "$.start.args.name"})
                Map<?, ?> config = (Map<?, ?>) valueExpr;
                String path = (String) config.get("extractPath");
                if (path != null) {
                    resolvedValue = resolveExpression(path, contextData, context);
                } else {
                    // Treat as raw object if no extractPath
                    resolvedValue = valueExpr;
                }
            } else {
                resolvedValue = valueExpr;
            }

            record.put(fieldName, resolvedValue);

            log.debug("Record [{}]: {} = {}", step.getId(), fieldName, resolvedValue);
        }

        log.info("Record [{}]: 构造对象: {}", step.getId(), record);

        // 存入上下文
        Map<String, Object> stepResult = new HashMap<>();
        stepResult.put(ContextKeys.RESULT, record);
        context.setVar(step.getId(), stepResult);

        return PortNames.OUT;
    }

    /**
     * 解析表达式获取值
     * 支持:
     *   1. JsonPath: "$." 开头 (如 "$.start.args.name")
     *   2. ${var.path}: 变量引用
     *   3. 字面量: 直接返回
     */
    private Object resolveExpression(Object expr, Map<String, Object> contextData, ExecutionContext context) {
        if (expr == null) return null;

        if (!(expr instanceof String)) {
            return expr; // 非字符串直接返回 (数字、布尔等)
        }

        String strExpr = ((String) expr).trim();

        // 1. JsonPath 格式: $.xxx.yyy
        if (strExpr.startsWith("$.") || strExpr.startsWith("$['")) {
            try {
                return JsonPath.read(contextData, strExpr);
            } catch (PathNotFoundException e) {
                return null;
            }
        }

        // 2. ${var.path} 格式
        Matcher matcher = VAR_PATTERN.matcher(strExpr);
        if (matcher.matches()) {
            String varPath = matcher.group(1);
            return resolveNestedVar(varPath, context);
        }

        // 3. 字面量
        return strExpr;
    }

    /**
     * 解析嵌套变量路径 (如 "evaluate.result")
     */
    @SuppressWarnings("unchecked")
    private Object resolveNestedVar(String path, ExecutionContext context) {
        String[] parts = path.split("\\.");
        Object current = context.getVariable(parts[0]);

        for (int i = 1; i < parts.length && current != null; i++) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(parts[i]);
            } else {
                return null;
            }
        }

        return current;
    }
}
