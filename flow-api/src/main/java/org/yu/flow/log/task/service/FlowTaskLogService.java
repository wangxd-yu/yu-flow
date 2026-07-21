package org.yu.flow.log.task.service;

import org.springframework.data.domain.Page;
import org.yu.flow.log.task.domain.FlowTaskLogDO;
import org.yu.flow.log.task.dto.FlowTaskLogDTO;
import org.yu.flow.log.task.dto.FlowTaskLogListDTO;
import org.yu.flow.log.task.query.FlowTaskLogQueryDTO;

/**
 * 任务日志服务接口
 *
 * @author yu-flow
 */
public interface FlowTaskLogService {

    /** 保存一条日志（由调度器调用） */
    FlowTaskLogDO save(FlowTaskLogDO log);

    /** 异步落库（对齐第三方日志，不阻塞 Cron / 请求线程） */
    void saveAsync(FlowTaskLogDO log);

    /** 分页查询列表（轻量，不含大字段） */
    Page<FlowTaskLogListDTO> pageList(FlowTaskLogQueryDTO queryDTO);

    /** 按 ID 查询完整详情（含 traceData） */
    FlowTaskLogDTO getById(String id);

    /** 清空某任务的全部日志 */
    void clearByTaskId(String taskId);
}
