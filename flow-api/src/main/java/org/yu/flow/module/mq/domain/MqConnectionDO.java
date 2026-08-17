package org.yu.flow.module.mq.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MQ 连接配置 JPA 实体（flow_mq_connection）
 *
 * <p>password 列持久化 AES 密文；对外 DTO 不回传密码。</p>
 *
 * @author yu-flow
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_mq_connection")
@org.hibernate.annotations.SQLDelete(sql = "update flow_mq_connection set deleted = 1 where id = ?")
@org.hibernate.annotations.Where(clause = "deleted = 0 OR deleted IS NULL")
public class MqConnectionDO implements Serializable {

    /** 健康状态常量 */
    public static final String HEALTH_HEALTHY = "HEALTHY";
    public static final String HEALTH_UNHEALTHY = "UNHEALTHY";
    public static final String HEALTH_UNKNOWN = "UNKNOWN";

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 连接名称 */
    private String name;

    /** 连接编码（唯一），流程 DSL / MQ 任务通过 code 引用 */
    @Column(nullable = false, length = 64)
    private String code;

    /** MQ 类型：RABBITMQ / KAFKA */
    @Column(name = "mq_type", nullable = false, length = 32)
    private String mqType;

    /** 服务器地址 host:port，多个逗号分隔（Kafka 即 bootstrap.servers） */
    @Column(nullable = false, length = 512)
    private String servers;

    /** 虚拟主机（仅 RabbitMQ，默认 /） */
    @Column(name = "virtual_host", length = 128)
    private String virtualHost;

    /** 用户名（可空；Kafka 有值时启用 SASL/PLAIN） */
    @Column(length = 128)
    private String username;

    /** 密码（AES 密文存储） */
    @Column(length = 512)
    private String password;

    /** 启用状态：0=停用，1=启用 */
    @Column(nullable = false)
    private Boolean enabled;

    /** 健康状态：HEALTHY / UNHEALTHY / UNKNOWN */
    @Column(name = "health_status", length = 32)
    private String healthStatus;

    /** 最近一次连接测试错误信息 */
    @Column(name = "last_error_msg", length = 1024)
    private String lastErrorMsg;

    /** 最近一次连接测试时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "last_test_time")
    private LocalDateTime lastTestTime;

    /** 备注描述 */
    private String info;

    /** 是否已删除：0=正常，1=已删除 */
    @Column(name = "deleted", columnDefinition = "int default 0")
    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
