package org.yu.flow.log.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.log.task.domain.FlowTaskLogDO;

import java.util.Date;

/**
 * 任务日志【完整详情 DTO】（含 traceData 大字段，用于快照回放）
 */
@Data
public class FlowTaskLogDTO {

    private String id;
    private String taskId;
    private String taskName;
    private String triggerType;
    private String status;
    private Long costTimeMs;
    private String errorMsg;
    private String traceData;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public static FlowTaskLogDTO fromDO(FlowTaskLogDO entity) {
        if (entity == null) return null;
        FlowTaskLogDTO dto = new FlowTaskLogDTO();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setTaskName(entity.getTaskName());
        dto.setTriggerType(entity.getTriggerType());
        dto.setStatus(entity.getStatus());
        dto.setCostTimeMs(entity.getCostTimeMs());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setTraceData(entity.getTraceData());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
