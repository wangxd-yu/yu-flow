package org.yu.flow.engine.evaluator.executor;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.JsonMapStep;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * jsonMap：按 mappings 从 payload / 上下文提取字段，输出映射对象。
 */
@Slf4j
public class JsonMapStepExecutor extends AbstractStepExecutor<JsonMapStep> {

    private static final Pattern VAR_PATTERN = Pattern.compile("^\\$\\{(.+)}$");
    private static final Pattern RELATIVE_IDENT = Pattern.compile("^[a-zA-Z_][\\w]*(\\.[a-zA-Z_][\\w]*)*$");
    private static final String PAYLOAD_KEY = "payload";

    @Override
    public String execute(JsonMapStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = prepareInputs(step, context, flow);
        Object payload = inputs != null ? inputs.get(PAYLOAD_KEY) : null;
        Map<String, Object> contextData = context.getVar();
        Map<String, Object> mapped = new LinkedHashMap<>();

        List<JsonMapStep.MapField> mappings = step.getMappings();
        if (mappings != null) {
            for (JsonMapStep.MapField field : mappings) {
                if (field == null || field.getTarget() == null || field.getTarget().isBlank()) {
                    continue;
                }
                Object value = resolveSource(field.getSource(), contextData, context, payload);
                mapped.put(field.getTarget().trim(), value);
            }
        }

        Map<String, Object> stepResult = new LinkedHashMap<>();
        stepResult.put(PortNames.OUT, mapped);
        context.setVar(step.getId(), stepResult);
        log.debug("JsonMap [{}]: keys={}", step.getId(), mapped.keySet());
        return PortNames.OUT;
    }

    private Object resolveSource(String source, Map<String, Object> contextData,
                                   ExecutionContext context, Object payload) {
        if (source == null) {
            return null;
        }
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        Matcher matcher = VAR_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return resolveNestedVar(matcher.group(1), context);
        }

        if (isContextAbsolute(trimmed, contextData)) {
            try {
                return JsonPath.read(contextData, trimmed);
            } catch (PathNotFoundException e) {
                return null;
            }
        }

        if (payload != null && isRelativePath(trimmed)) {
            return resolveAgainstPayload(trimmed, payload);
        }

        if (trimmed.startsWith("$.") || trimmed.startsWith("$['")) {
            try {
                return JsonPath.read(contextData, trimmed);
            } catch (Exception e) {
                return null;
            }
        }

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
        String first = after.contains(".") ? after.substring(0, after.indexOf('.')) : after;
        return contextData != null && contextData.containsKey(first);
    }

    private boolean isRelativePath(String path) {
        if ("$".equals(path) || path.startsWith("$.")) {
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
            return JsonPath.read(payload, jsonPath);
        } catch (PathNotFoundException e) {
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
