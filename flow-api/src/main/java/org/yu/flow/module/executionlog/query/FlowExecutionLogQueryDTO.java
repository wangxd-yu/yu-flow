package org.yu.flow.module.executionlog.query;

import lombok.Data;

@Data
public class FlowExecutionLogQueryDTO {
    private Integer page = 0;
    private Integer size = 10;
    private String apiId;
    private String apiName;
    private String status;
    private String method;
}
