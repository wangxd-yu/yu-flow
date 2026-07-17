package org.yu.flow.log.execution.service;

import org.springframework.data.domain.Page;
import org.yu.flow.log.execution.domain.FlowExecutionLogDO;
import org.yu.flow.log.execution.dto.FlowExecutionLogDTO;
import org.yu.flow.log.execution.dto.FlowExecutionLogListDTO;
import org.yu.flow.log.execution.query.FlowExecutionLogQueryDTO;

public interface FlowExecutionLogService {
    void saveLogAsync(FlowExecutionLogDO logDO);

    /**
     * 列表分页查询（轻量 DTO，不含大字段，供大盘列表使用）
     */
    Page<FlowExecutionLogListDTO> pageList(FlowExecutionLogQueryDTO queryDTO);

    /**
     * 按 ID 查询完整详情（含 traceData/requestParams/responseBody 等大字段）
     */
    FlowExecutionLogDTO getById(String id);
}
