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
 * 发送 MQ 消息节点：委托 {@link MqSendService}（经 MqProvider SPI 路由 RabbitMQ / Kafka）。
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
            throw new FlowException("MQ_CONNECTION_REQUIRED", "MQ 连接编码 connectionCode 不能为空", step.getId());
        }
        if (StrUtil.isBlank(topic)) {
            throw new FlowException("MQ_TOPIC_REQUIRED", "目标 topic 不能为空", step.getId());
        }
        if (message == null || message.isEmpty()) {
            throw new FlowException("MQ_MESSAGE_REQUIRED", "消息体 message 不能为空", step.getId());
        }

        MqSendService sendService;
        try {
            sendService = SpringUtil.getBean(MqSendService.class);
        } catch (Exception e) {
            throw new FlowException("MQ_SERVICE_UNAVAILABLE", "MQ 发送服务不可用: " + e.getMessage(), step.getId());
        }
        if (sendService == null) {
            throw new FlowException("MQ_NOT_CONFIGURED",
                    "MQ 发送服务未装配，请检查 MQ 连接配置", step.getId());
        }

        long start = System.currentTimeMillis();
        MqSendResult sendResult;
        try {
            sendResult = sendService.send(connectionCode, topic, messageKey, headers, message);
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("MQ_SEND_ERROR", "MQ 消息发送失败: " + e.getMessage(), step.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("messageId", sendResult != null ? sendResult.getMessageId() : null);
        result.put("topic", topic);
        result.put("timeMs", System.currentTimeMillis() - start);
        Map<String, Object> out = new HashMap<>();
        out.put(PortNames.OUT, result);
        context.setVar(step.getId(), out);
        return PortNames.OUT;
    }

    /** inputs.message 为对象时自动 JSON 序列化；字符串走 ${var} 模板替换 */
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

    /** headers 值为字符串时支持 ${var} 模板替换 */
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
            Object val = inputs.get(varName);
            String replacement = val != null ? String.valueOf(val) : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
