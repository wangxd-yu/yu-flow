package org.yu.flow.log.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.log.service.domain.FlowServiceLogDO;

import java.util.Date;

@Data
public class FlowServiceLogDTO {

    private String id;
    private String serviceId;
    private String serviceName;
    private String triggerType;
    private String status;
    private Long costTimeMs;
    private String errorMsg;
    private String traceData;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public static FlowServiceLogDTO fromDO(FlowServiceLogDO entity) {
        if (entity == null) return null;
        FlowServiceLogDTO dto = new FlowServiceLogDTO();
        dto.setId(entity.getId());
        dto.setServiceId(entity.getServiceId());
        dto.setServiceName(entity.getServiceName());
        dto.setTriggerType(entity.getTriggerType());
        dto.setStatus(entity.getStatus());
        dto.setCostTimeMs(entity.getCostTimeMs());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setTraceData(entity.getTraceData());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
