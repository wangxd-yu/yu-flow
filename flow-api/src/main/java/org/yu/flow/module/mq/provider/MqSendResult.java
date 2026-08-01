package org.yu.flow.module.mq.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MQ 消息发送结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MqSendResult {

    /** 消息 ID（Rabbit 为 messageId 属性；Kafka 为 topic-partition@offset） */
    private String messageId;

    /** 实际发送的 topic / 队列名 */
    private String topic;
}
