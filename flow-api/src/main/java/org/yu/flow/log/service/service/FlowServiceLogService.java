package org.yu.flow.log.service.service;

import org.springframework.data.domain.Page;
import org.yu.flow.log.service.domain.FlowServiceLogDO;
import org.yu.flow.log.service.dto.FlowServiceLogDTO;
import org.yu.flow.log.service.dto.FlowServiceLogListDTO;
import org.yu.flow.log.service.query.FlowServiceLogQueryDTO;

public interface FlowServiceLogService {

    FlowServiceLogDO save(FlowServiceLogDO log);

    /** 异步落库（对齐第三方日志，不阻塞请求线程） */
    void saveAsync(FlowServiceLogDO log);

    Page<FlowServiceLogListDTO> pageList(FlowServiceLogQueryDTO queryDTO);

    FlowServiceLogDTO getById(String id);

    void clearByServiceId(String serviceId);
}
