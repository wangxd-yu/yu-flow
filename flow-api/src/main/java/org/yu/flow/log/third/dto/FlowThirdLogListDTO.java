package org.yu.flow.log.third.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.log.third.domain.FlowThirdLogDO;

import java.util.Date;

/**
 * 三方日志列表轻量 DTO（不含 requestParams/requestHeaders/responseBody/curl）
 */
@Data
public class FlowThirdLogListDTO {
    private String id;
    private String apiType;
    private String source;
    private String sourceRef;
    private String sourceName;
    private String requestUrl;
    private String requestMethod;
    private Integer responseStatus;
    private Long elapsedTime;
    private Integer isSuccess;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public FlowThirdLogListDTO() {
    }

    /** JPA Criteria {@code cb.construct} 专用构造器（勿传入 TEXT 大字段） */
    public FlowThirdLogListDTO(String id, String apiType, String source, String sourceRef,
                               String sourceName, String requestUrl, String requestMethod,
                               Integer responseStatus, Long elapsedTime, Integer isSuccess,
                               Date createTime) {
        this.id = id;
        this.apiType = apiType;
        this.source = source;
        this.sourceRef = sourceRef;
        this.sourceName = sourceName;
        this.requestUrl = requestUrl;
        this.requestMethod = requestMethod;
        this.responseStatus = responseStatus;
        this.elapsedTime = elapsedTime;
        this.isSuccess = isSuccess;
        this.createTime = createTime;
    }

    /** @deprecated 列表查询请走投影，避免加载 TEXT 大字段 */
    @Deprecated
    public static FlowThirdLogListDTO fromDO(FlowThirdLogDO entity) {
        if (entity == null) {
            return null;
        }
        return new FlowThirdLogListDTO(
                entity.getId(),
                entity.getApiType(),
                entity.getSource(),
                entity.getSourceRef(),
                entity.getSourceName(),
                entity.getRequestUrl(),
                entity.getRequestMethod(),
                entity.getResponseStatus(),
                entity.getElapsedTime(),
                entity.getIsSuccess(),
                entity.getCreateTime()
        );
    }
}
