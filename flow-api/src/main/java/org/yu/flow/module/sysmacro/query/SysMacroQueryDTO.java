package org.yu.flow.module.sysmacro.query;

import lombok.Data;

/**
 * 系统宏定义分页查询条件 DTO
 */
@Data
public class SysMacroQueryDTO {

    /** 宏编码（模糊匹配） */
    private String macroCode;

    /** 宏名称（模糊匹配） */
    private String macroName;

    /** 宏类型精确过滤 (VARIABLE / FUNCTION) */
    private String macroType;

    /** 作用域精确过滤 (ALL / SQL_ONLY / JS_ONLY) */
    private String scope;

    /** 状态过滤 (1: 启用, 0: 停用) */
    private Integer status;

    /** 当前页码，从 1 开始 */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;
}
