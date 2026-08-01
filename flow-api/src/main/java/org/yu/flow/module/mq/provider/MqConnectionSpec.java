package org.yu.flow.module.mq.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MQ 连接规格：由连接配置（flow_mq_connection）解密后构造，传递给 {@link MqProvider}。
 *
 * <p>与 DO 解耦，Provider 层不感知持久化模型。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MqConnectionSpec {

    /** MQ 类型常量 */
    public static final String TYPE_RABBITMQ = "RABBITMQ";
    public static final String TYPE_KAFKA = "KAFKA";

    /** 连接配置编码（flow_mq_connection.code），作为客户端缓存 key */
    private String code;

    /** MQ 类型：RABBITMQ / KAFKA */
    private String mqType;

    /** 服务器地址，host:port，多个逗号分隔（Kafka 即 bootstrap.servers） */
    private String servers;

    /** 虚拟主机（仅 RabbitMQ） */
    private String virtualHost;

    /** 用户名（可空；Kafka 有值时启用 SASL/PLAIN） */
    private String username;

    /** 密码（已解密明文） */
    private String password;

    /** 配置指纹：连接参数变化时用于判断客户端缓存是否需要重建 */
    public String fingerprint() {
        return String.join("|",
                nullToEmpty(servers), nullToEmpty(virtualHost),
                nullToEmpty(username), nullToEmpty(password));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
