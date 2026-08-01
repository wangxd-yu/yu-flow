package org.yu.flow.module.mq.provider;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RabbitMQ Provider：按连接配置动态构建 {@link CachingConnectionFactory}，
 * 不依赖全局 spring.rabbitmq 自动装配。
 *
 * <p>topic 语义映射为「默认交换机 + durable 队列」：发送即 routingKey=队列名，
 * 发送/订阅前自动幂等声明队列，开箱即用。</p>
 */
@Slf4j
public class RabbitMqProvider implements MqProvider {

    /** connectionCode -> 客户端缓存 */
    private final Map<String, CachedClient> clients = new ConcurrentHashMap<>();

    private static class CachedClient {
        final String fingerprint;
        final CachingConnectionFactory connectionFactory;
        final RabbitTemplate template;
        final RabbitAdmin admin;
        /** 已声明过的队列（幂等声明去重） */
        final Set<String> declaredQueues = ConcurrentHashMap.newKeySet();

        CachedClient(String fingerprint, CachingConnectionFactory factory) {
            this.fingerprint = fingerprint;
            this.connectionFactory = factory;
            this.template = new RabbitTemplate(factory);
            this.admin = new RabbitAdmin(factory);
        }
    }

    @Override
    public String getType() {
        return MqConnectionSpec.TYPE_RABBITMQ;
    }

    @Override
    public MqSendResult send(MqConnectionSpec spec, String topic, String messageKey,
                             Map<String, Object> headers, String payload, long timeoutMs) {
        CachedClient client = getOrCreateClient(spec);
        declareQueueIfAbsent(client, topic);

        String messageId = StrUtil.isNotBlank(messageKey) ? messageKey : UUID.randomUUID().toString();
        MessageProperties props = new MessageProperties();
        props.setMessageId(messageId);
        props.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        if (headers != null) {
            headers.forEach(props::setHeader);
        }
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);
        // 默认交换机 + routingKey=队列名
        client.template.send("", topic, message);
        return MqSendResult.builder().messageId(messageId).topic(topic).build();
    }

    @Override
    public MqSubscription subscribe(MqConnectionSpec spec, String topic, String consumerGroup,
                                    int concurrency, MqMessageListener listener) {
        CachedClient client = getOrCreateClient(spec);
        declareQueueIfAbsent(client, topic);

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(client.connectionFactory);
        container.setQueueNames(topic);
        container.setConcurrentConsumers(Math.max(1, concurrency));
        container.setMessageListener(message -> listener.onMessage(convert(topic, message)));
        container.start();
        log.info("[MQ][Rabbit] 已订阅队列 {} (connection={}, concurrency={})", topic, spec.getCode(), concurrency);

        return new MqSubscription() {
            @Override
            public boolean isRunning() {
                return container.isRunning();
            }

            @Override
            public void close() {
                try {
                    container.stop();
                } catch (Exception e) {
                    log.warn("[MQ][Rabbit] 停止订阅失败 queue={}: {}", topic, e.getMessage());
                }
            }
        };
    }

    @Override
    public void testConnection(MqConnectionSpec spec) {
        CachingConnectionFactory factory = buildFactory(spec);
        try {
            factory.createConnection().close();
        } finally {
            factory.destroy();
        }
    }

    @Override
    public void invalidate(String connectionCode) {
        CachedClient client = clients.remove(connectionCode);
        if (client != null) {
            try {
                client.connectionFactory.destroy();
            } catch (Exception e) {
                log.warn("[MQ][Rabbit] 销毁连接工厂失败 code={}: {}", connectionCode, e.getMessage());
            }
        }
    }

    private CachedClient getOrCreateClient(MqConnectionSpec spec) {
        String fingerprint = spec.fingerprint();
        CachedClient existing = clients.get(spec.getCode());
        if (existing != null && !existing.fingerprint.equals(fingerprint)) {
            invalidate(spec.getCode());
            existing = null;
        }
        if (existing != null) {
            return existing;
        }
        return clients.computeIfAbsent(spec.getCode(),
                code -> new CachedClient(fingerprint, buildFactory(spec)));
    }

    private CachingConnectionFactory buildFactory(MqConnectionSpec spec) {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setAddresses(spec.getServers());
        if (StrUtil.isNotBlank(spec.getUsername())) {
            factory.setUsername(spec.getUsername());
        }
        if (StrUtil.isNotBlank(spec.getPassword())) {
            factory.setPassword(spec.getPassword());
        }
        if (StrUtil.isNotBlank(spec.getVirtualHost())) {
            factory.setVirtualHost(spec.getVirtualHost());
        }
        return factory;
    }

    private void declareQueueIfAbsent(CachedClient client, String queue) {
        if (client.declaredQueues.contains(queue)) {
            return;
        }
        client.admin.declareQueue(QueueBuilder.durable(queue).build());
        client.declaredQueues.add(queue);
    }

    private static MqMessage convert(String topic, Message message) {
        MessageProperties props = message.getMessageProperties();
        String messageId = props != null && StrUtil.isNotBlank(props.getMessageId())
                ? props.getMessageId() : UUID.randomUUID().toString();
        Map<String, Object> headers = new LinkedHashMap<>();
        if (props != null && props.getHeaders() != null) {
            headers.putAll(props.getHeaders());
        }
        return MqMessage.builder()
                .messageId(messageId)
                .topic(topic)
                .body(new String(message.getBody(), StandardCharsets.UTF_8))
                .headers(headers)
                .build();
    }
}
