package org.yu.flow.log.open.query;

import lombok.Data;

@Data
public class FlowOpenCallLogQueryDTO {
    private String platformId;
    private String appKey;
    private String path;
    private Integer status;
    private String errorCode;
    /** yyyy-MM-dd HH:mm:ss */
    private String startTime;
    private String endTime;
    private Integer page = 0;
    private Integer size = 20;
}
