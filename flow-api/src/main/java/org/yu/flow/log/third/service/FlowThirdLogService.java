package org.yu.flow.log.third.service;

import org.springframework.data.domain.Page;
import org.yu.flow.log.third.domain.FlowThirdLogDO;
import org.yu.flow.log.third.dto.FlowThirdLogDTO;
import org.yu.flow.log.third.dto.FlowThirdLogListDTO;
import org.yu.flow.log.third.query.FlowThirdLogQueryDTO;

public interface FlowThirdLogService {

    void saveLogAsync(FlowThirdLogDO logDO);

    Page<FlowThirdLogListDTO> pageList(FlowThirdLogQueryDTO queryDTO);

    FlowThirdLogDTO getById(String id);
}
