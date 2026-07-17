package org.yu.flow.log.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.log.task.domain.FlowTaskLogDO;

import java.util.Date;

/**
 * 任务日志【列表轻量 DTO】
 *
 * <p>仅包含列表展示所需的摘要字段，剔除 traceData 等大字段。
 * 详细信息请通过 {@code GET /{id}} 接口获取 {@link FlowTaskLogDTO}。
 */
@Data
public class FlowTaskLogListDTO {

    private String id;
    private String taskId;
    private String taskName;
    /** 触发类型：CRON / MANUAL */
    private String triggerType;
    /** 执行状态：SUCCESS / FAILED / RUNNING */
    private String status;
    /** 耗时（毫秒） */
    private Long costTimeMs;
    /** 是否含有 Trace 快照（前端据此决定是否显示「查看快照」按钮） */
    private Boolean hasTrace;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public FlowTaskLogListDTO() {
    }

    /**
     * JPA Criteria {@code cb.construct} 专用构造器。
     * hasTrace 由 SQL {@code CASE WHEN trace_data IS NOT NULL} 计算，勿加载 LOB。
     */
    public FlowTaskLogListDTO(String id, String taskId, String taskName, String triggerType,
                              String status, Long costTimeMs, Boolean hasTrace, Date createTime) {
        this.id = id;
        this.taskId = taskId;
        this.taskName = taskName;
        this.triggerType = triggerType;
        this.status = status;
        this.costTimeMs = costTimeMs;
        this.hasTrace = hasTrace;
        this.createTime = createTime;
    }

    /** @deprecated 列表查询请走投影，避免加载 LONGTEXT */
    @Deprecated
    public static FlowTaskLogListDTO fromDO(FlowTaskLogDO entity) {
        if (entity == null) return null;
        return new FlowTaskLogListDTO(
                entity.getId(),
                entity.getTaskId(),
                entity.getTaskName(),
                entity.getTriggerType(),
                entity.getStatus(),
                entity.getCostTimeMs(),
                entity.getTraceData() != null && !entity.getTraceData().isEmpty(),
                entity.getCreateTime()
        );
    }
}
