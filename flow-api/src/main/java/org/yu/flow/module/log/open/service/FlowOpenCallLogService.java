package org.yu.flow.module.log.open.service;

import org.springframework.data.domain.Page;
import org.yu.flow.module.log.open.domain.FlowOpenCallLogDO;
import org.yu.flow.module.log.open.dto.FlowOpenCallLogListDTO;
import org.yu.flow.module.log.open.query.FlowOpenCallLogQueryDTO;

public interface FlowOpenCallLogService {

    void saveLogAsync(FlowOpenCallLogDO logDO);

    Page<FlowOpenCallLogListDTO> pageList(FlowOpenCallLogQueryDTO query);
}
