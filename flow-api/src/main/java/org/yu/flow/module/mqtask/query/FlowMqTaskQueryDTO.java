package org.yu.flow.module.mqtask.query;

import lombok.Data;

/**
 * MQ 任务分页查询条件
 *
 * @author yu-flow
 */
@Data
public class FlowMqTaskQueryDTO {

    /** 目录ID（精确匹配） */
    private String directoryId;

    /** 任务名称（模糊匹配） */
    private String name;

    /** 绑定 MQ 连接编码（精确匹配） */
    private String connectionCode;

    /** 启用状态：0=停用，1=启用 */
    private Boolean enabled;

    /** 发布状态：0=未发布，1=已发布 */
    private Integer publishStatus;

    /** 页码（从 0 开始，默认 0） */
    private int page = 0;

    /** 每页条数（默认 10） */
    private int size = 10;
}
