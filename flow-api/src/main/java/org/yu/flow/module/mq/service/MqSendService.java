package org.yu.flow.module.mq.service;

import org.yu.flow.module.mq.provider.MqSendResult;

import java.util.Map;

/**
 * MQ 消息发送服务：按连接编码路由 Provider 发送。
 *
 * <p>供 MqSendStepExecutor（流程节点）与后台管理调用。</p>
 *
 * @author yu-flow
 */
public interface MqSendService {

    /**
     * 发送一条消息。
     *
     * @param connectionCode MQ 连接编码（flow_mq_connection.code）
     * @param topic          目标 topic / 队列名
     * @param messageKey     消息 key（Kafka 分区键 / Rabbit messageId），可空
     * @param headers        消息头，可空
     * @param message        消息体（UTF-8 文本）
     * @return 发送结果（messageId / topic）
     * @throws org.yu.flow.exception.FlowException 连接不存在、消息超限或发送失败
     */
    MqSendResult send(String connectionCode, String topic, String messageKey,
                      Map<String, Object> headers, String message);
}
