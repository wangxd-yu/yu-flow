package org.yu.flow.module.mqtask.domain;

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
 * MQ 任务定义 JPA 实体（mqTrigger 入口流程资产）
 *
 * <p>照 {@code FlowTaskDO} 模式：草稿 DSL + 发布快照 + 软删除。
 * 与定时任务的差异在于触发配置：Cron 换成 connectionCode/topic/consumerGroup/concurrency。</p>
 *
 * @author yu-flow
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_mq_task_info")
@org.hibernate.annotations.SQLDelete(sql = "update flow_mq_task_info set deleted = 1 where id = ?")
@org.hibernate.annotations.Where(clause = "deleted = 0 OR deleted IS NULL")
public class FlowMqTaskDO implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 任务名称 */
    private String name;

    /** 关联全局目录树 */
    private String directoryId;

    /** 绑定 MQ 连接编码（flow_mq_connection.code） */
    @Column(nullable = false, length = 64)
    private String connectionCode;

    /** 订阅 topic / 队列名 */
    @Column(nullable = false)
    private String topic;

    /** 消费组（Kafka group.id；Rabbit 忽略） */
    @Column(length = 128)
    private String consumerGroup;

    /** 消费并发数（默认 1） */
    @Column(columnDefinition = "int default 1")
    private Integer concurrency;

    /** 启用状态：0=停用，1=启用 */
    @Column(columnDefinition = "tinyint(1) default 1")
    private Boolean enabled;

    /** 是否记录执行日志 */
    @Column(columnDefinition = "tinyint(1) default 1")
    private Boolean logEnabled;

    /** 日志保留天数：null=跟随系统配置，0=永久保留，>0=自定义天数 */
    private Integer logRetentionDays;

    /** 流程定义 DSL JSON（草稿） */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String dslContent;

    /** 发布状态：0=未发布，1=已发布 */
    @Column(columnDefinition = "tinyint default 0")
    private Integer publishStatus;

    /** 发布快照 JSON：dslContent + 订阅配置 */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String publishedSnapshot;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    /** 任务描述 */
    private String info;

    /** 标签，英文逗号分隔 */
    private String tags;

    /** 是否已删除：0=正常，1=已删除 */
    @Column(name = "deleted", columnDefinition = "int default 0")
    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
