package org.yu.flow.module.api.query;

import lombok.Data;

/**
 * FlowApi 分页查询条件
 *
 * @author yu-flow
 */
@Data
public class FlowApiQueryDTO {

    /** 目录ID（精确匹配） */
    private String directoryId;

    /** 接口名称（模糊匹配） */
    private String name;

    /** 请求方法：GET/POST/PUT/DELETE（精确匹配） */
    private String method;

    /** 接口地址（模糊匹配） */
    private String url;

    /** 发布状态：0-未发布，1-已发布（精确匹配） */
    private Integer publishStatus;

    /** 页码（从 0 开始，默认 0） */
    private int page = 0;

    /** 每页条数（默认 10） */
    private int size = 10;
}
