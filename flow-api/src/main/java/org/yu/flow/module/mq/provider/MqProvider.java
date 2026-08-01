package org.yu.flow.module.mq.provider;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MQ Provider SPI：屏蔽 RabbitMQ / Kafka 等中间件差异的统一接口。
 *
 * <p>实现类按 {@link MqConnectionSpec} 动态构建客户端并按 code 缓存，
 * 连接配置变更/删除时经 {@link #invalidate(String)} 销毁重建。</p>
 */
public interface MqProvider {

    /** 支持的 MQ 类型（MqConnectionSpec.TYPE_*） */
    String getType();

    /**
     * 发送消息。
     *
     * @param spec       连接规格
     * @param topic      目标 topic（Rabbit 为队列名 / routingKey）
     * @param messageKey 消息 Key（Kafka 分区键 / Rabbit messageId 候选），可空
     * @param headers    附加消息头，可空
     * @param payload    消息体（UTF-8 文本）
     * @param timeoutMs  发送确认超时（毫秒）
     */
    MqSendResult send(MqConnectionSpec spec, String topic, String messageKey,
                      Map<String, Object> headers, String payload, long timeoutMs);

    /**
     * 订阅 topic，收到消息回调 listener。
     *
     * @param consumerGroup 消费组（Kafka group.id；Rabbit 竞争消费天然分组，仅作标识）
     * @param concurrency   并发消费者数
     */
    MqSubscription subscribe(MqConnectionSpec spec, String topic, String consumerGroup,
                             int concurrency, MqMessageListener listener);

    /**
     * 测试连接可达性，失败抛异常（异常信息用于管理页提示）。
     * 使用临时客户端，不进入缓存。
     */
    void testConnection(MqConnectionSpec spec);

    /**
     * 列出可订阅的 topic 候选，供表单下拉提示（非集群管理能力）。
     *
     * <p>默认返回空表示该中间件不支持枚举（如 RabbitMQ 无 AMQP 层队列列举能力），
     * 此时前端保持手工输入。实现方需自行按 keyword 过滤并截断到 limit。</p>
     *
     * @param keyword 名称模糊匹配关键字，可空表示不过滤
     * @param limit   最大返回条数
     */
    default List<String> listTopics(MqConnectionSpec spec, String keyword, int limit) {
        return Collections.emptyList();
    }

    /** 销毁指定连接编码的缓存客户端（配置变更/删除时调用） */
    void invalidate(String connectionCode);
}
