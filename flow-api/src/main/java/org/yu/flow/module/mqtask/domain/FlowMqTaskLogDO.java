package org.yu.flow.module.mqtask.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * MQ 任务执行日志 JPA 实体
 *
 * @author yu-flow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "flow_log_mq_task")
public class FlowMqTaskLogDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 关联 MQ 任务ID */
    @Column(nullable = false, length = 32)
    private String taskId;

    /** 任务名称（冗余，避免频繁关联查询） */
    private String taskName;

    /** 消息 topic / 队列名 */
    private String topic;

    /** 消息ID（幂等去重键） */
    private String messageId;

    /** 触发类型：MQ=消息触发，MANUAL=手动模拟 */
    @Column(length = 16)
    private String triggerType;

    /** 执行状态：SUCCESS / FAILED / RUNNING / SKIPPED */
    @Column(length = 16)
    private String status;

    /** 耗时（毫秒） */
    private Long costTimeMs;

    /** 失败信息 */
    @Column(columnDefinition = "TEXT")
    private String errorMsg;

    /** 原始消息体（JSON 解析前的字符串，支持超限截断） */
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGTEXT")
    private String messageBody;

    /** 消息头 JSON 字典 */
    @Column(columnDefinition = "TEXT")
    private String messageHeaders;

    /** FlowTrace JSON 快照（logEnabled=true 时记录）；列表查询勿 SELECT 此字段 */
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGTEXT")
    private String traceData;

    /** 执行开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        }
    }
}
