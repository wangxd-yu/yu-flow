package org.yu.flow.module.task.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.dto.FlowTaskDTO;
import org.yu.flow.module.task.query.FlowTaskQueryDTO;

import java.util.List;

/**
 * 定时任务业务服务接口
 *
 * @author yu-flow
 */
public interface FlowTaskService {

    /** 创建任务 */
    FlowTaskDO save(FlowTaskDO taskDO);

    /** 更新任务（含 cron/dslContent 等字段，自动重新调度） */
    FlowTaskDO update(FlowTaskDO taskDO);

    /** 删除任务（并取消调度） */
    void delete(String id);

    /** 批量删除 */
    void batchDelete(List<String> ids);

    /** 按 ID 查询 */
    FlowTaskDO findById(String id);

    /** 分页查询 */
    PageBean<FlowTaskDTO> findPage(FlowTaskQueryDTO queryDTO);

    /** 启用任务（注册调度器） */
    FlowTaskDO enable(String id);

    /** 停用任务（取消调度器） */
    FlowTaskDO disable(String id);

    /** 更新日志开关 */
    FlowTaskDO updateLogEnabled(String id, boolean logEnabled);
}
