package org.yu.flow.module.mqtask.query;

import lombok.Data;

/**
 * MQ 任务日志分页查询条件
 *
 * @author yu-flow
 */
@Data
public class FlowMqTaskLogQueryDTO {
    private Integer page = 0;
    private Integer size = 10;
    /** 任务ID（精确匹配，从任务列表页跳转时传入） */
    private String taskId;
    /** 任务名称（模糊搜索） */
    private String taskName;
    /** 执行状态：SUCCESS / FAILED / RUNNING / SKIPPED */
    private String status;
    /** 触发类型：MQ / MANUAL */
    private String triggerType;
    /** 查询开始时间（ISO 8601） */
    private String startTime;
    /** 查询结束时间（ISO 8601） */
    private String endTime;
}
