package org.yu.flow.module.executionlog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.executionlog.domain.FlowExecutionLogDO;

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

    /**
     * 从 DO 转换（不加载 LONGTEXT 字段）
     */
    public static FlowExecutionLogListDTO fromDO(FlowExecutionLogDO entity) {
        if (entity == null) return null;
        FlowExecutionLogListDTO dto = new FlowExecutionLogListDTO();
        dto.setId(entity.getId());
        dto.setApiId(entity.getApiId());
        dto.setApiName(entity.getApiName());
        dto.setUrl(entity.getUrl());
        dto.setServiceType(entity.getServiceType());
        dto.setMethod(entity.getMethod());
        dto.setStatus(entity.getStatus());
        dto.setCostTimeMs(entity.getCostTimeMs());
        // 有 traceData 才允许前端打开"查看快照"
        dto.setHasTrace(entity.getTraceData() != null && !entity.getTraceData().isEmpty());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
