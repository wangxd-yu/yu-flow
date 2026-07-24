package org.yu.flow.log.third.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.log.third.domain.FlowThirdLogDO;

import java.time.LocalDateTime;

@Data
public class FlowThirdLogDTO {
    private String id;
    private String apiType;
    private String source;
    private String sourceRef;
    private String sourceName;
    private String requestUrl;
    private String requestMethod;
    private String requestParams;
    private String requestHeaders;
    private Integer responseStatus;
    private String responseBody;
    private Long elapsedTime;
    private Integer isSuccess;
    private String errorMessage;
    private String curl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static FlowThirdLogDTO fromDO(FlowThirdLogDO entity) {
        if (entity == null) {
            return null;
        }
        FlowThirdLogDTO dto = new FlowThirdLogDTO();
        dto.setId(entity.getId());
        dto.setApiType(entity.getApiType());
        dto.setSource(entity.getSource());
        dto.setSourceRef(entity.getSourceRef());
        dto.setSourceName(entity.getSourceName());
        dto.setRequestUrl(entity.getRequestUrl());
        dto.setRequestMethod(entity.getRequestMethod());
        dto.setRequestParams(entity.getRequestParams());
        dto.setRequestHeaders(entity.getRequestHeaders());
        dto.setResponseStatus(entity.getResponseStatus());
        dto.setResponseBody(entity.getResponseBody());
        dto.setElapsedTime(entity.getElapsedTime());
        dto.setIsSuccess(entity.getIsSuccess());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCurl(entity.getCurl());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
