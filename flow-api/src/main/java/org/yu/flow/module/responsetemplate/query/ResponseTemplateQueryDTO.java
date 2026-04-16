package org.yu.flow.module.responsetemplate.query;

import lombok.Data;

/**
 * API 响应模板分页查询条件 DTO
 */
@Data
public class ResponseTemplateQueryDTO {

    /** 模板名称（模糊匹配） */
    private String templateName;

    /** 当前页码，从 1 开始 */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;
}
