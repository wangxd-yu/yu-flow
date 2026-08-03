package org.yu.flow.engine.evaluator.executor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.MqSendStep;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.mq.provider.MqSendResult;
import org.yu.flow.module.mq.service.MqSendService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发送 MQ 消息节点：成功走 success，失败走 fail（软失败，不抛异常）。
 */
@Slf4j
public class MqSendStepExecutor extends AbstractStepExecutor<MqSendStep> {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String execute(MqSendStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = prepareInputs(step, context, flow);

        String connectionCode = firstNonBlank(str(inputs.get("connectionCode")),
                resolveString(step.getConnectionCode(), inputs));
        String topic = firstNonBlank(str(inputs.get("topic")), resolveString(step.getTopic(), inputs));
        String messageKey = firstNonBlank(str(inputs.get("messageKey")),
                resolveString(step.getMessageKey(), inputs));
        String message = resolveMessage(step, inputs);
        Map<String, Object> headers = resolveHeaders(step.getHeaders(), inputs);

        if (StrUtil.isBlank(connectionCode)) {
            return softFail(step, context, "MQ 连接编码 connectionCode 不能为空");
        }
        if (StrUtil.isBlank(topic)) {
            return softFail(step, context, "目标 topic 不能为空");
        }
        if (message == null || message.isEmpty()) {
            return softFail(step, context, "消息体 message 不能为空");
        }

        MqSendService sendService;
        try {
            sendService = SpringUtil.getBean(MqSendService.class);
        } catch (Exception e) {
            return softFail(step, context, "MQ 发送服务不可用: " + e.getMessage());
        }
        if (sendService == null) {
            return softFail(step, context, "MQ 发送服务未装配，请检查 MQ 连接配置");
        }

        long start = System.currentTimeMillis();
        try {
            MqSendResult sendResult = sendService.send(connectionCode, topic, messageKey, headers, message);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("messageId", sendResult != null ? sendResult.getMessageId() : null);
            result.put("topic", topic);
            result.put("timeMs", System.currentTimeMillis() - start);
            Map<String, Object> out = new HashMap<>();
            out.put(PortNames.OUT, result);
            context.setVar(step.getId(), out);
            return PortNames.SUCCESS;
        } catch (Exception e) {
            log.warn("MqSend [{}] 失败: {}", step.getId(), e.getMessage());
            return softFail(step, context, e.getMessage());
        }
    }

    private String softFail(MqSendStep step, ExecutionContext context, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", message);
        Map<String, Object> out = new HashMap<>();
        out.put(PortNames.OUT, result);
        context.setVar(step.getId(), out);
        return PortNames.FAIL;
    }

    private static String resolveMessage(MqSendStep step, Map<String, Object> inputs) {
        Object bound = inputs.get("message");
        if (bound != null && !(bound instanceof String)) {
            try {
                return MAPPER.writeValueAsString(bound);
            } catch (Exception e) {
                throw new FlowException("MQ_MESSAGE_SERIALIZE_ERROR",
                        "消息体 JSON 序列化失败: " + e.getMessage(), step.getId());
            }
        }
        return firstNonBlank(str(bound), resolveString(step.getMessage(), inputs));
    }

    private static Map<String, Object> resolveHeaders(Map<String, Object> headers, Map<String, Object> inputs) {
        if (headers == null || headers.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                resolved.put(entry.getKey(), resolveString((String) value, inputs));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }

    private static String resolveString(String template, Map<String, Object> inputs) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object val = getValueByPath(varName, inputs);
            String replacement = val != null ? String.valueOf(val) : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Object getValueByPath(String path, Map<String, Object> inputs) {
        if (path == null || inputs == null || inputs.isEmpty()) {
            return null;
        }
        if (inputs.containsKey(path)) {
            return inputs.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = inputs;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String a, String b) {
        if (StrUtil.isNotBlank(a)) {
            return a;
        }
        return b;
    }
}
