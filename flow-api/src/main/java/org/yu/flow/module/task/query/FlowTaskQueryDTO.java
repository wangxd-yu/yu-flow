package org.yu.flow.module.task.query;

import lombok.Data;

/**
 * 定时任务分页查询条件
 *
 * @author yu-flow
 */
@Data
public class FlowTaskQueryDTO {

    /** 目录ID（精确匹配） */
    private String directoryId;

    /** 任务名称（模糊匹配） */
    private String name;

    /** 启用状态：0=停用，1=启用 */
    private Boolean enabled;

    /** 页码（从 0 开始，默认 0） */
    private int page = 0;

    /** 每页条数（默认 10） */
    private int size = 10;
}
