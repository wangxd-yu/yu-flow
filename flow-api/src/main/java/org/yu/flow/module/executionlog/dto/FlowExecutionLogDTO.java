package org.yu.flow.module.executionlog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.executionlog.domain.FlowExecutionLogDO;

import java.util.Date;

@Data
public class FlowExecutionLogDTO {
    private String id;
    private String apiId;
    private String apiName;
    private String url;
    private String serviceType;
    private String method;
    private String requestParams;
    private String responseBody;
    private String status;
    private String errorMsg;
    private Long costTimeMs;
    private String traceData;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public static FlowExecutionLogDTO fromDO(FlowExecutionLogDO entity) {
        if (entity == null) {
            return null;
        }
        FlowExecutionLogDTO dto = new FlowExecutionLogDTO();
        dto.setId(entity.getId());
        dto.setApiId(entity.getApiId());
        dto.setApiName(entity.getApiName());
        dto.setUrl(entity.getUrl());
        dto.setServiceType(entity.getServiceType());
        dto.setMethod(entity.getMethod());
        dto.setRequestParams(entity.getRequestParams());
        dto.setResponseBody(entity.getResponseBody());
        dto.setStatus(entity.getStatus());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setCostTimeMs(entity.getCostTimeMs());
        dto.setTraceData(entity.getTraceData());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
