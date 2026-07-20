package org.yu.flow.log.service.query;

import lombok.Data;

@Data
public class FlowServiceLogQueryDTO {
    private Integer page = 0;
    private Integer size = 10;
    private String serviceId;
    private String serviceName;
    private String status;
    private String triggerType;
    private String startTime;
    private String endTime;
}
