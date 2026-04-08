package org.yu.flow.module.page.query;

import lombok.Data;

/**
 * PageInfo 分页查询条件
 *
 * @author yu-flow
 */
@Data
public class PageInfoQueryDTO {

    /** 目录ID（精确匹配） */
    private String directoryId;

    /** 页面名称（模糊匹配） */
    private String name;

    /** 访问路径（模糊匹配） */
    private String routePath;

    /** 页码（从 0 开始，默认 0） */
    private int page = 0;

    /** 每页条数（默认 10） */
    private int size = 10;
}
