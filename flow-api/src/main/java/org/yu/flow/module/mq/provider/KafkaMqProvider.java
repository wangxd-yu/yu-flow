package org.yu.flow.module.mq.provider;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Kafka Provider：按连接配置动态构建 {@link DefaultKafkaProducerFactory} / 监听容器，
 * 不依赖全局 spring.kafka 自动装配。
 *
 * <p>username 有值时启用 SASL/PLAIN（SASL_PLAINTEXT）。消费侧 messageId 为
 * {@code topic-partition@offset}，天然幂等。</p>
 */
@Slf4j
public class KafkaMqProvider implements MqProvider {

    private static final String ADVERTISED_HINT =
            "常见原因是 broker 的 advertised.listeners 返回了客户端无法解析的主机名，"
                    + "请将其配置为客户端可达的 IP/域名，或在本机 hosts 中补充这些主机名映射";

    /** connectionCode -> 生产者缓存 */
    private final Map<String, CachedProducer> producers = new ConcurrentHashMap<>();

    private static class CachedProducer {
        final String fingerprint;
        final DefaultKafkaProducerFactory<String, String> factory;
        final KafkaTemplate<String, String> template;

        CachedProducer(String fingerprint, DefaultKafkaProducerFactory<String, String> factory) {
            this.fingerprint = fingerprint;
            this.factory = factory;
            this.template = new KafkaTemplate<>(factory);
        }
    }

    @Override
    public String getType() {
        return MqConnectionSpec.TYPE_KAFKA;
    }

    @Override
    public MqSendResult send(MqConnectionSpec spec, String topic, String messageKey,
                             Map<String, Object> headers, String payload, long timeoutMs) {
        CachedProducer producer = getOrCreateProducer(spec);

        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic, StrUtil.isBlank(messageKey) ? null : messageKey, payload);
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (value != null) {
                    record.headers().add(key, String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                }
            });
        }

        try {
            SendResult<String, String> result = producer.template.send(record)
                    .get(Math.max(1000L, timeoutMs), TimeUnit.MILLISECONDS);
            String messageId = topic + "-" + result.getRecordMetadata().partition()
                    + "@" + result.getRecordMetadata().offset();
            return MqSendResult.builder().messageId(messageId).topic(topic).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 发送被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 发送失败: " + rootMessage(e), e);
        }
    }

    @Override
    public MqSubscription subscribe(MqConnectionSpec spec, String topic, String consumerGroup,
                                    int concurrency, MqMessageListener listener) {
        Map<String, Object> props = commonProps(spec);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                StrUtil.isBlank(consumerGroup) ? "yu-flow-" + spec.getCode() : consumerGroup);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(props);
        ContainerProperties containerProps = new ContainerProperties(topic);
        containerProps.setMessageListener((MessageListener<String, String>) record -> {
            Map<String, Object> messageHeaders = new LinkedHashMap<>();
            record.headers().forEach(header ->
                    messageHeaders.put(header.key(), new String(header.value(), StandardCharsets.UTF_8)));
            listener.onMessage(MqMessage.builder()
                    .messageId(record.topic() + "-" + record.partition() + "@" + record.offset())
                    .topic(record.topic())
                    .body(record.value())
                    .headers(messageHeaders)
                    .build());
        });

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
        container.setConcurrency(Math.max(1, concurrency));
        container.start();
        log.info("[MQ][Kafka] 已订阅 topic {} (connection={}, group={}, concurrency={})",
                topic, spec.getCode(), consumerGroup, concurrency);

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
                    log.warn("[MQ][Kafka] 停止订阅失败 topic={}: {}", topic, e.getMessage());
                }
            }
        };
    }

    @Override
    public void testConnection(MqConnectionSpec spec) {
        // 先 TCP 预检 bootstrap，避免把"地址不通"和"元数据不可用"混成同一个超时
        List<String> unreachable = new ArrayList<>();
        if (probeBootstrap(spec.getServers(), unreachable) <= 0) {
            throw new IllegalStateException("Kafka 连接失败: bootstrap 地址不可达 - "
                    + String.join("; ", unreachable));
        }

        Map<String, Object> props = commonProps(spec);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
        Collection<Node> nodes;
        String clusterId;
        try (AdminClient admin = AdminClient.create(props)) {
            DescribeClusterResult cluster = admin.describeCluster();
            nodes = cluster.nodes().get(8, TimeUnit.SECONDS);
            clusterId = cluster.clusterId().get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 连接测试被中断", e);
        } catch (Exception e) {
            if (isTimeout(e)) {
                // bootstrap 端口通、元数据却拿不到，绝大多数是广告地址不可解析
                throw new IllegalStateException("Kafka 连接失败: bootstrap 端口可达但获取集群元数据超时。"
                        + ADVERTISED_HINT, e);
            }
            throw new IllegalStateException("Kafka 连接失败: " + rootMessage(e), e);
        }

        // 元数据可用不代表能收发：客户端后续按广告地址直连各 broker
        List<String> unresolved = unresolvedNodes(nodes);
        if (!unresolved.isEmpty()) {
            throw new IllegalStateException("Kafka 连接失败: broker 广告地址无法解析 - "
                    + String.join(", ", unresolved) + "。" + ADVERTISED_HINT);
        }
        log.info("[MQ][Kafka] 连接测试成功 servers={} clusterId={} brokers={}",
                spec.getServers(), clusterId, nodes == null ? 0 : nodes.size());
    }

    /** 逐个 bootstrap 做 DNS + TCP 探测，返回可达数量，失败原因写入 errors */
    private static int probeBootstrap(String servers, List<String> errors) {
        int reachable = 0;
        for (String raw : StrUtil.split(StrUtil.nullToEmpty(servers), ',')) {
            String hostPort = raw.trim();
            if (hostPort.isEmpty()) {
                continue;
            }
            int sep = hostPort.lastIndexOf(':');
            String host = sep > 0 ? hostPort.substring(0, sep) : hostPort;
            int port = 9092;
            if (sep > 0) {
                try {
                    port = Integer.parseInt(hostPort.substring(sep + 1).trim());
                } catch (NumberFormatException e) {
                    errors.add(hostPort + " (端口格式非法)");
                    continue;
                }
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 3000);
                reachable++;
            } catch (Exception e) {
                errors.add(hostPort + " (" + rootMessage(e) + ")");
            }
        }
        if (errors.isEmpty() && reachable == 0) {
            errors.add("服务器地址为空");
        }
        return reachable;
    }

    /** 返回本机无法解析的 broker 广告地址（host:port） */
    private static List<String> unresolvedNodes(Collection<Node> nodes) {
        List<String> unresolved = new ArrayList<>();
        if (nodes == null) {
            return unresolved;
        }
        for (Node node : nodes) {
            if (node == null || StrUtil.isBlank(node.host())) {
                continue;
            }
            try {
                InetAddress.getByName(node.host());
            } catch (Exception e) {
                unresolved.add(node.host() + ":" + node.port());
            }
        }
        return unresolved;
    }

    /** Kafka 客户端超时会包装为 common.errors.TimeoutException，与 JDK 超时一并识别 */
    private static boolean isTimeout(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.util.concurrent.TimeoutException
                    || cause instanceof org.apache.kafka.common.errors.TimeoutException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public List<String> listTopics(MqConnectionSpec spec, String keyword, int limit) {
        Map<String, Object> props = commonProps(spec);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
        Set<String> names;
        try (AdminClient admin = AdminClient.create(props)) {
            names = admin.listTopics().names().get(8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取 Kafka topic 列表被中断", e);
        } catch (Exception e) {
            if (isTimeout(e)) {
                throw new IllegalStateException("获取 Kafka topic 列表超时。" + ADVERTISED_HINT, e);
            }
            throw new IllegalStateException("获取 Kafka topic 列表失败: " + rootMessage(e), e);
        }

        String kw = StrUtil.isBlank(keyword) ? null : keyword.trim().toLowerCase();
        int max = limit <= 0 ? 50 : Math.min(limit, 500);
        List<String> matched = new ArrayList<>();
        for (String name : names) {
            // 过滤 __consumer_offsets 等内部 topic，不作为业务订阅候选
            if (name == null || name.startsWith("_")) {
                continue;
            }
            if (kw == null || name.toLowerCase().contains(kw)) {
                matched.add(name);
            }
        }
        Collections.sort(matched);
        return matched.size() > max ? new ArrayList<>(matched.subList(0, max)) : matched;
    }

    @Override
    public void invalidate(String connectionCode) {
        CachedProducer producer = producers.remove(connectionCode);
        if (producer != null) {
            try {
                producer.factory.destroy();
            } catch (Exception e) {
                log.warn("[MQ][Kafka] 销毁生产者工厂失败 code={}: {}", connectionCode, e.getMessage());
            }
        }
    }

    private CachedProducer getOrCreateProducer(MqConnectionSpec spec) {
        String fingerprint = spec.fingerprint();
        CachedProducer existing = producers.get(spec.getCode());
        if (existing != null && !existing.fingerprint.equals(fingerprint)) {
            invalidate(spec.getCode());
            existing = null;
        }
        if (existing != null) {
            return existing;
        }
        return producers.computeIfAbsent(spec.getCode(), code -> {
            Map<String, Object> props = commonProps(spec);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            return new CachedProducer(fingerprint, new DefaultKafkaProducerFactory<>(props));
        });
    }

    private Map<String, Object> commonProps(MqConnectionSpec spec) {
        Map<String, Object> props = new HashMap<>();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, spec.getServers());
        if (StrUtil.isNotBlank(spec.getUsername())) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
            props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            props.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                    spec.getUsername(), spec.getPassword() == null ? "" : spec.getPassword()));
        }
        return props;
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
