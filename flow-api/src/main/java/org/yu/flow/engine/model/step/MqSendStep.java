package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.Step;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 发送 MQ 消息节点：经 MqProvider SPI 向 RabbitMQ / Kafka 等发送消息。
 *
 * <p>字段支持 {@code ${var}} 替换（var 来自 inputs）。也可用 inputs.topic / message 等覆盖。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class MqSendStep extends Step {

    /** MQ 连接配置编码（flow_mq_connection.code） */
    private String connectionCode;

    /** 目标 topic（Rabbit 为 routingKey / 队列名），支持 ${var} */
    private String topic;

    /** 消息 Key（Kafka 分区键 / Rabbit messageId 候选），支持 ${var}，可空 */
    private String messageKey;

    /** 消息体模板，支持 ${var}；inputs.message 为对象时自动 JSON 序列化 */
    private String message;

    /** 附加消息头，值支持 ${var} */
    private Map<String, Object> headers;

    @Override
    public String getType() {
        return NodeType.MQ_SEND;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
