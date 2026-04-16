package org.yu.flow.module.sysconfig.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 新建和更新系统配置的 DTO
 */
@Data
public class SaveSysConfigDTO {

    /** 主键，更新时必传 */
    private String id;

    /** 配置键 (唯一标识，如: SYSTEM_PREFIX, TOKEN_EXPIRE) */
    @NotBlank(message = "配置键不能为空")
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型 (STRING / NUMBER / BOOLEAN / JSON) */
    @NotBlank(message = "值类型不能为空")
    private String valueType;

    /** 配置分组 (GENERAL / SECURITY / OSS / GATEWAY 等) */
    private String configGroup;

    /** 配置说明 */
    private String remark;

    /** 是否内置 (1: 内置, 0: 自定义)，新增时默认 0 */
    private Integer isBuiltin;

    /** 状态 (1: 启用, 0: 停用) */
    private Integer status;
}
