package org.yu.flow.module.sysconfig.query;

import lombok.Data;

/**
 * 系统配置分页查询条件 DTO
 */
@Data
public class SysConfigQueryDTO {

    /** 配置键（模糊匹配） */
    private String configKey;

    /** 配置分组精确过滤 (GENERAL / SECURITY / OSS / GATEWAY) */
    private String configGroup;

    /** 状态过滤 (1: 启用, 0: 停用) */
    private Integer status;

    /** 当前页码，从 1 开始 */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;
}
