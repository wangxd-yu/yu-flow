package org.yu.flow.log.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class FlowServiceLogListDTO {

    private String id;
    private String serviceId;
    private String serviceName;
    private String triggerType;
    private String status;
    private Long costTimeMs;
    private Boolean hasTrace;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public FlowServiceLogListDTO() {
    }

    public FlowServiceLogListDTO(String id, String serviceId, String serviceName, String triggerType,
                                 String status, Long costTimeMs, Boolean hasTrace, Date createTime) {
        this.id = id;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.triggerType = triggerType;
        this.status = status;
        this.costTimeMs = costTimeMs;
        this.hasTrace = hasTrace;
        this.createTime = createTime;
    }
}
