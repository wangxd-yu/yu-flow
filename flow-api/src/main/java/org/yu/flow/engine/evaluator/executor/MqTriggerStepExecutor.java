package org.yu.flow.engine.evaluator.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.MqTriggerStep;

import java.util.HashMap;
import java.util.Map;

/**
 * MqTrigger 消息队列触发入口节点执行器
 *
 * <p>将消息元信息（topic、message、headers、messageId、triggerTime）注入执行上下文，
 * 供下游节点通过 {@code $.mq.message} / {@code $.mq.headers} 等路径引用。
 *
 * <p>消息由 MqConsumerManager 经 execute(args) 注入以下上下文变量：
 * {@code __mqTopic} / {@code __mqMessage} / {@code __mqHeaders} / {@code __mqMessageId}。
 * 消息体若为合法 JSON 自动解析为对象，否则保留原始字符串。
 *
 * <p>该节点无输入端口，不接受外部参数，执行后直接返回 {@code "out"} 端口。
 */
public class MqTriggerStepExecutor extends AbstractStepExecutor<MqTriggerStep> {

    /** 消费者注入的上下文变量名 */
    public static final String ARG_TOPIC = "__mqTopic";
    public static final String ARG_MESSAGE = "__mqMessage";
    public static final String ARG_HEADERS = "__mqHeaders";
    public static final String ARG_MESSAGE_ID = "__mqMessageId";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String execute(MqTriggerStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> mqData = new HashMap<>();
        mqData.put("topic", strOrEmpty(context.getVariable(ARG_TOPIC)));
        mqData.put("message", parseMessage(context.getVariable(ARG_MESSAGE)));
        mqData.put("headers", context.getVariable(ARG_HEADERS));
        mqData.put("messageId", strOrEmpty(context.getVariable(ARG_MESSAGE_ID)));
        mqData.put("triggerTime", System.currentTimeMillis());
        context.setVar("mq", mqData);
        return PortNames.OUT;
    }

    /** 消息体为 JSON 时自动解析为对象/数组，解析失败保留原始字符串 */
    private static Object parseMessage(Object raw) {
        if (!(raw instanceof String)) {
            return raw;
        }
        String text = ((String) raw).trim();
        if ((text.startsWith("{") && text.endsWith("}"))
                || (text.startsWith("[") && text.endsWith("]"))) {
            try {
                return MAPPER.readValue(text, Object.class);
            } catch (Exception ignore) {
                // 非合法 JSON，按原始字符串处理
            }
        }
        return raw;
    }

    private static String strOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
