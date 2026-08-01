package org.yu.flow.module.mqtask.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskDTO;
import org.yu.flow.module.mqtask.query.FlowMqTaskQueryDTO;

import java.util.List;

/**
 * MQ 任务业务服务接口
 *
 * @author yu-flow
 */
public interface FlowMqTaskService {

    /** 创建任务 */
    FlowMqTaskDO save(FlowMqTaskDO taskDO);

    /** 更新任务（含订阅配置/dslContent 等字段，自动调整订阅） */
    FlowMqTaskDO update(FlowMqTaskDO taskDO);

    /** 删除任务（并取消订阅） */
    void delete(String id);

    /** 批量删除 */
    void batchDelete(List<String> ids);

    /** 按 ID 查询 */
    FlowMqTaskDO findById(String id);

    /** 分页查询 */
    PageBean<FlowMqTaskDTO> findPage(FlowMqTaskQueryDTO queryDTO);

    /** 启用任务（注册消费订阅） */
    FlowMqTaskDO enable(String id);

    /** 停用任务（取消消费订阅） */
    FlowMqTaskDO disable(String id);

    /** 更新日志开关 */
    FlowMqTaskDO updateLogEnabled(String id, boolean logEnabled);

    /** 发布（生成快照并按快照重新订阅） */
    FlowMqTaskDO publish(String id);

    /** 下线（清空快照并取消订阅） */
    FlowMqTaskDO unpublish(String id);

    /** 草稿回滚到已发布快照 */
    FlowMqTaskDO rollbackToPublished(String id);
}
