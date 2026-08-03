package org.yu.flow.module.mqtask.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.mqtask.domain.FlowMqTaskLogDO;

import java.time.LocalDateTime;

/**
 * MQ 任务日志【列表轻量 DTO】
 *
 * <p>仅包含列表展示所需的摘要字段，剔除 traceData / messageBody 等大字段。
 * 详细信息请通过 {@code GET /{id}} 接口获取 {@link FlowMqTaskLogDTO}。
 */
@Data
public class FlowMqTaskLogListDTO {

    private String id;
    private String taskId;
    private String taskName;
    private String topic;
    private String messageId;
    /** 触发类型：MQ / MANUAL */
    private String triggerType;
    /** 执行状态：SUCCESS / FAILED / RUNNING / SKIPPED */
    private String status;
    /** 耗时（毫秒） */
    private Long costTimeMs;
    /** 失败/跳过原因摘要（TEXT，非 LOB，列表可安全投影） */
    private String errorMsg;
    /** 是否含有 Trace 快照（前端据此决定是否显示「查看快照」按钮） */
    private Boolean hasTrace;
    /** 是否含有原始报文（前端据此决定「原始报文」按钮可用性） */
    private Boolean hasMessageBody;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public FlowMqTaskLogListDTO() {
    }

    /**
     * JPA Criteria {@code cb.construct} 专用构造器。
     * hasTrace / hasMessageBody 由 SQL {@code CASE WHEN … IS NOT NULL} 计算，勿加载 LOB。
     */
    public FlowMqTaskLogListDTO(String id, String taskId, String taskName, String topic,
                                String messageId, String triggerType, String status,
                                Long costTimeMs, String errorMsg, Boolean hasTrace,
                                Boolean hasMessageBody, LocalDateTime createTime) {
        this.id = id;
        this.taskId = taskId;
        this.taskName = taskName;
        this.topic = topic;
        this.messageId = messageId;
        this.triggerType = triggerType;
        this.status = status;
        this.costTimeMs = costTimeMs;
        this.errorMsg = errorMsg;
        this.hasTrace = hasTrace;
        this.hasMessageBody = hasMessageBody;
        this.createTime = createTime;
    }

    /** @deprecated 列表查询请走投影，避免加载 LONGTEXT */
    @Deprecated
    public static FlowMqTaskLogListDTO fromDO(FlowMqTaskLogDO entity) {
        if (entity == null) return null;
        return new FlowMqTaskLogListDTO(
                entity.getId(),
                entity.getTaskId(),
                entity.getTaskName(),
                entity.getTopic(),
                entity.getMessageId(),
                entity.getTriggerType(),
                entity.getStatus(),
                entity.getCostTimeMs(),
                entity.getErrorMsg(),
                entity.getTraceData() != null && !entity.getTraceData().isEmpty(),
                entity.getMessageBody() != null && !entity.getMessageBody().isEmpty(),
                entity.getCreateTime()
        );
    }
}
