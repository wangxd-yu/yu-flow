package org.yu.flow.engine.evaluator.executor;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.RedisStep;
import org.yu.flow.exception.FlowException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redis 节点：get / set / del / incr，基于 {@link FlowRedisUtil}。
 */
@Slf4j
public class RedisStepExecutor extends AbstractStepExecutor<RedisStep> {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public String execute(RedisStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = prepareInputs(step, context, flow);
        String op = step.getOperation() == null ? "get" : step.getOperation().trim().toLowerCase();
        String key = resolveString(step.getKey(), inputs);
        if (key == null || key.isBlank()) {
            throw new FlowException("REDIS_KEY_REQUIRED", "Redis 节点 [" + step.getId() + "] key 不能为空", step.getId());
        }

        Object result;
        switch (op) {
            case "get" -> result = FlowRedisUtil.get(key);
            case "set" -> {
                String value = resolveString(step.getValue(), inputs);
                Long ttl = step.getTtlSeconds();
                if (ttl != null && ttl > 0) {
                    FlowRedisUtil.set(key, value, ttl, TimeUnit.SECONDS);
                } else {
                    FlowRedisUtil.set(key, value);
                }
                result = value;
            }
            case "del" -> result = FlowRedisUtil.delete(key);
            case "incr" -> {
                long delta = 1L;
                String valStr = resolveString(step.getValue(), inputs);
                if (valStr != null && !valStr.isBlank()) {
                    try {
                        delta = Long.parseLong(valStr.trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                result = FlowRedisUtil.incr(key, delta);
            }
            default -> throw new FlowException("REDIS_OP_UNSUPPORTED",
                    "Redis 节点 [" + step.getId() + "] 不支持 operation=" + op, step.getId());
        }

        Map<String, Object> out = new HashMap<>();
        out.put(PortNames.OUT, result);
        context.setVar(step.getId(), out);
        log.debug("Redis [{}] {} key={} -> {}", step.getId(), op, key, result);
        return PortNames.OUT;
    }

    private static String resolveString(String template, Map<String, Object> inputs) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object val = inputs.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val.toString() : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
