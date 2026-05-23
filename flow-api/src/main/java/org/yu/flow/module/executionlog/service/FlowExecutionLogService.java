package org.yu.flow.module.executionlog.service;

import org.springframework.data.domain.Page;
import org.yu.flow.module.executionlog.domain.FlowExecutionLogDO;
import org.yu.flow.module.executionlog.dto.FlowExecutionLogDTO;
import org.yu.flow.module.executionlog.query.FlowExecutionLogQueryDTO;

public interface FlowExecutionLogService {
    void saveLogAsync(FlowExecutionLogDO logDO);
    
    Page<FlowExecutionLogDTO> pageQuery(FlowExecutionLogQueryDTO queryDTO);
    
    FlowExecutionLogDTO getById(String id);
}
