package org.yu.flow.log.execution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.log.execution.domain.FlowExecutionLogDO;

import java.util.Date;

/**
 * API 执行日志【列表轻量 DTO】
 *
 * <p>仅包含列表展示所需的摘要字段，剔除了所有 LONGTEXT 大字段（requestParams/responseBody/traceData/errorMsg）。
 * <p>详细信息（含大字段）请通过 {@code GET /{id}} 接口获取 {@link FlowExecutionLogDTO}。
 */
@Data
public class FlowExecutionLogListDTO {
    private String id;
    private String apiId;
    private String apiName;
    private String url;
    /** 接口类型：FLOW / DB / JSON / STRING */
    private String serviceType;
    private String method;
    /** 执行状态：SUCCESS / ERROR */
    private String status;
    /** 耗时（毫秒） */
    private Long costTimeMs;
    /** 是否含有 Trace 快照（前端据此决定是否显示"查看快照"按钮） */
    private Boolean hasTrace;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public FlowExecutionLogListDTO() {
    }

    /** JPA Criteria {@code cb.construct} 专用构造器（勿传入 LOB 字段） */
    public FlowExecutionLogListDTO(String id, String apiId, String apiName, String url,
                                   String serviceType, String method, String status,
                                   Long costTimeMs, Boolean hasTrace, Date createTime) {
        this.id = id;
        this.apiId = apiId;
        this.apiName = apiName;
        this.url = url;
        this.serviceType = serviceType;
        this.method = method;
        this.status = status;
        this.costTimeMs = costTimeMs;
        this.hasTrace = hasTrace;
        this.createTime = createTime;
    }

    /** @deprecated 列表查询请走投影，避免加载 LONGTEXT */
    @Deprecated
    public static FlowExecutionLogListDTO fromDO(FlowExecutionLogDO entity) {
        if (entity == null) return null;
        return new FlowExecutionLogListDTO(
                entity.getId(),
                entity.getApiId(),
                entity.getApiName(),
                entity.getUrl(),
                entity.getServiceType(),
                entity.getMethod(),
                entity.getStatus(),
                entity.getCostTimeMs(),
                entity.getTraceData() != null && !entity.getTraceData().isEmpty(),
                entity.getCreateTime()
        );
    }
}
