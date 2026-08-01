package org.yu.flow.module.mq.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 消费到的 MQ 消息（跨中间件统一模型）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MqMessage {

    /** 消息 ID（Rabbit 取 messageId 属性，缺省生成 UUID；Kafka 为 topic-partition@offset，天然幂等） */
    private String messageId;

    /** 来源 topic / 队列名 */
    private String topic;

    /** 消息体（UTF-8 文本） */
    private String body;

    /** 消息头 */
    private Map<String, Object> headers;
}
