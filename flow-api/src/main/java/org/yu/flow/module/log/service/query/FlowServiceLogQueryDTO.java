package org.yu.flow.module.log.service.query;

import lombok.Data;

@Data
public class FlowServiceLogQueryDTO {
    private Integer page = 0;
    private Integer size = 10;
    private String serviceId;
    private String serviceName;
    private String status;
    private String triggerType;
    /** 请求 ID 精确匹配（响应头 X-Request-Id） */
    private String requestId;
    private String startTime;
    private String endTime;
}
