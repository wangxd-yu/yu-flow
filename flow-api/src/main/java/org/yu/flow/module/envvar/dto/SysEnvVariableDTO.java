package org.yu.flow.module.envvar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.envvar.domain.SysEnvVariableDO;

import java.time.LocalDateTime;

/**
 * 环境变量列表项；敏感变量不回传值，只告诉前端是否已设置。
 */
@Data
public class SysEnvVariableDTO {

    private String id;

    private String code;

    /** secret=true 时恒为 null */
    private String value;

    private boolean secret;

    private boolean valueSet;

    private String remark;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static SysEnvVariableDTO fromDO(SysEnvVariableDO entity) {
        SysEnvVariableDTO dto = new SysEnvVariableDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setSecret(Boolean.TRUE.equals(entity.getSecret()));
        dto.setValue(dto.isSecret() ? null : entity.getValue());
        dto.setValueSet(entity.getValue() != null && !entity.getValue().isEmpty());
        dto.setRemark(entity.getRemark());
        dto.setUpdateBy(entity.getUpdateBy());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
