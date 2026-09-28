package org.yu.flow.module.envvar.dto;

import lombok.Data;

/**
 * 新建 / 修改环境变量。
 */
@Data
public class SaveSysEnvVariableDTO {

    /** 仅新建时生效；变量名被编排引用，创建后不可改 */
    private String code;

    /** 修改敏感变量时传 null 表示保留原值 */
    private String value;

    private Boolean secret;

    private String remark;
}
