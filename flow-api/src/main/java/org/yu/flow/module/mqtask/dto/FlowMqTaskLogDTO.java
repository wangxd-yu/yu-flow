package org.yu.flow.module.mqtask.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.mqtask.domain.FlowMqTaskLogDO;

import java.time.LocalDateTime;

/**
 * MQ 任务日志【完整详情 DTO】（含 traceData 大字段，用于快照回放）
 */
@Data
public class FlowMqTaskLogDTO {

    private String id;
    private String taskId;
    private String taskName;
    private String topic;
    private String messageId;
    private String triggerType;
    private String status;
    private Long costTimeMs;
    private String errorMsg;
    private String messageBody;
    private String messageHeaders;
    private String traceData;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static FlowMqTaskLogDTO fromDO(FlowMqTaskLogDO entity) {
        if (entity == null) return null;
        FlowMqTaskLogDTO dto = new FlowMqTaskLogDTO();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setTaskName(entity.getTaskName());
        dto.setTopic(entity.getTopic());
        dto.setMessageId(entity.getMessageId());
        dto.setTriggerType(entity.getTriggerType());
        dto.setStatus(entity.getStatus());
        dto.setCostTimeMs(entity.getCostTimeMs());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setMessageBody(entity.getMessageBody());
        dto.setMessageHeaders(entity.getMessageHeaders());
        dto.setTraceData(entity.getTraceData());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
