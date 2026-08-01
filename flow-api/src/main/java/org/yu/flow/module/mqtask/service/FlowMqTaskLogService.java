package org.yu.flow.module.mqtask.service;

import org.springframework.data.domain.Page;
import org.yu.flow.module.mqtask.domain.FlowMqTaskLogDO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskLogDTO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskLogListDTO;
import org.yu.flow.module.mqtask.query.FlowMqTaskLogQueryDTO;

/**
 * MQ 任务日志服务接口
 *
 * @author yu-flow
 */
public interface FlowMqTaskLogService {

    /** 保存一条日志（由消费者管理器调用） */
    FlowMqTaskLogDO save(FlowMqTaskLogDO log);

    /** 异步落库（不阻塞消费线程） */
    void saveAsync(FlowMqTaskLogDO log);

    /** 分页查询列表（轻量，不含大字段） */
    Page<FlowMqTaskLogListDTO> pageList(FlowMqTaskLogQueryDTO queryDTO);

    /** 按 ID 查询完整详情（含 traceData） */
    FlowMqTaskLogDTO getById(String id);

    /** 清空某任务的全部日志 */
    void clearByTaskId(String taskId);
}
