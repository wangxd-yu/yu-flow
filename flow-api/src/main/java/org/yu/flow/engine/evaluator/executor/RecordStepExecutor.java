package org.yu.flow.engine.evaluator.executor;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.RecordStep;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Record 数据构造节点执行器 (Object Builder)
 *
 * <p>支持两种传入方式（可并存）：
 * <ul>
 *   <li>逐字段连线：inputs 中绝对路径（{@code $.node.out}），覆盖同名 schema 键</li>
 *   <li>总入口 payload：{@code inputs.payload}；schema 中相对路径（{@code apiid} / {@code $.apiid}）相对该包解析</li>
 * </ul>
 */
@Slf4j
public class RecordStepExecutor extends AbstractStepExecutor<RecordStep> {

    private static final Pattern VAR_PATTERN = Pattern.compile("^\\$\\{(.+)}$");
    /** 相对路径：标识符或点分路径，如 apiid / user.name */
    private static final Pattern RELATIVE_IDENT = Pattern.compile("^[a-zA-Z_][\\w]*(\\.[a-zA-Z_][\\w]*)*$");

    private static final String PAYLOAD_KEY = "payload";

    @Override
    public String execute(RecordStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> schema = step.getSchema();
        Map<String, Object> record = new LinkedHashMap<>();
        Map<String, Object> contextData = context.getVar();

        Map<String, Object> inputs = prepareInputs(step, context, flow);
        Object payload = null;
        if (inputs != null && inputs.containsKey(PAYLOAD_KEY)) {
            payload = inputs.get(PAYLOAD_KEY);
        }

        if (schema != null && !schema.isEmpty()) {
            for (Map.Entry<String, Object> entry : schema.entrySet()) {
                String fieldName = entry.getKey();
                if (fieldName == null || fieldName.trim().isEmpty()) {
                    continue;
                }
                // 已被绝对路径 inputs 覆盖的键，schema 阶段可跳过（后面 overlay）
                if (inputs != null && inputs.containsKey(fieldName.trim())
                        && !PAYLOAD_KEY.equals(fieldName.trim())) {
                    continue;
                }
                Object resolvedValue = resolveFieldValue(
                        entry.getValue(), contextData, context, payload);
                record.put(fieldName.trim(), resolvedValue);
                log.debug("Record [{}]: {} = {}", step.getId(), fieldName, resolvedValue);
            }
        }

        // inputs 覆盖同名键（逐字段绝对连线）；payload 不写入输出对象
        if (inputs != null && !inputs.isEmpty()) {
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.trim().isEmpty() || PAYLOAD_KEY.equals(key.trim())) {
                    continue;
                }
                record.put(key.trim(), entry.getValue());
            }
        }

        if (record.isEmpty()) {
            log.warn("Record [{}]: schema/inputs 均为空", step.getId());
        } else {
            log.debug("Record [{}]: 构造对象 keys={}", step.getId(), record.keySet());
        }

        Map<String, Object> stepResult = new LinkedHashMap<>();
        stepResult.put(PortNames.OUT, record);
        stepResult.put("result", record);
        context.setVar(step.getId(), stepResult);

        return PortNames.OUT;
    }

    @SuppressWarnings("unchecked")
    private Object resolveFieldValue(Object valueExpr, Map<String, Object> contextData,
                                     ExecutionContext context, Object payload) {
        if (valueExpr instanceof String) {
            return resolveExpression((String) valueExpr, contextData, context, payload);
        }
        if (valueExpr instanceof Map) {
            Map<?, ?> config = (Map<?, ?>) valueExpr;
            Object pathObj = config.get("extractPath");
            if (pathObj instanceof String) {
                return resolveExpression((String) pathObj, contextData, context, payload);
            }
            return valueExpr;
        }
        return valueExpr;
    }

    /**
     * 解析优先级：
     * <ol>
     *   <li>上下文绝对路径 {@code $.nodeId...} / {@code ${ref}}</li>
     *   <li>有 payload 时的相对路径 {@code $} / {@code $.apiid} / {@code apiid}</li>
     *   <li>字面量</li>
     * </ol>
     */
    private Object resolveExpression(String strExpr, Map<String, Object> contextData,
                                     ExecutionContext context, Object payload) {
        if (strExpr == null) {
            return null;
        }
        String trimmed = strExpr.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        // 1) ${var.path}
        Matcher matcher = VAR_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return resolveNestedVar(matcher.group(1), context);
        }

        // 2) 上下文绝对 JsonPath
        if (isContextAbsolute(trimmed, contextData)) {
            try {
                return JsonPath.read(contextData, trimmed);
            } catch (PathNotFoundException e) {
                return null;
            } catch (Exception e) {
                log.debug("Record JsonPath 解析失败: {} -> {}", trimmed, e.getMessage());
                return null;
            }
        }

        // 3) 相对 payload
        if (payload != null && isRelativePath(trimmed)) {
            return resolveAgainstPayload(trimmed, payload);
        }

        // 4) 仍以 $. 开头但非绝对：尝试上下文（兼容）
        if (trimmed.startsWith("$.") || trimmed.startsWith("$['")) {
            try {
                return JsonPath.read(contextData, trimmed);
            } catch (Exception e) {
                return null;
            }
        }

        // 5) 字面量
        return trimmed;
    }

    private boolean isContextAbsolute(String path, Map<String, Object> contextData) {
        if (path.startsWith("$['")) {
            return true;
        }
        if (!path.startsWith("$.")) {
            return false;
        }
        String after = path.substring(2);
        String first = after.contains(".")
                ? after.substring(0, after.indexOf('.'))
                : after;
        return contextData != null && contextData.containsKey(first);
    }

    private boolean isRelativePath(String path) {
        if ("$".equals(path)) {
            return true;
        }
        if (path.startsWith("$.")) {
            // 单段 $.apiid 视为相对；多段且首段非节点 id 时也走相对（由调用方先判绝对）
            return true;
        }
        return RELATIVE_IDENT.matcher(path).matches();
    }

    private Object resolveAgainstPayload(String path, Object payload) {
        if ("$".equals(path)) {
            return payload;
        }
        String jsonPath = path.startsWith("$.") ? path : "$." + path;
        try {
            if (payload instanceof Map || payload instanceof Iterable || payload.getClass().isArray()) {
                return JsonPath.read(payload, jsonPath);
            }
            // 包装标量
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("value", payload);
            if ("$.value".equals(jsonPath) || "value".equals(path)) {
                return payload;
            }
            return JsonPath.read(wrap, jsonPath);
        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.debug("Record 相对 payload 解析失败: {} -> {}", path, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolveNestedVar(String path, ExecutionContext context) {
        String[] parts = path.split("\\.");
        Object current = context.getVariable(parts[0]);

        for (int i = 1; i < parts.length && current != null; i++) {
            if (!(current instanceof Map)) {
                return null;
            }
            Map<String, Object> map = (Map<String, Object>) current;
            Object next = map.get(parts[i]);
            if (next == null && "result".equals(parts[i])) {
                next = map.get(PortNames.OUT);
            } else if (next == null && PortNames.OUT.equals(parts[i])) {
                next = map.get("result");
            }
            current = next;
        }
        return current;
    }
}
