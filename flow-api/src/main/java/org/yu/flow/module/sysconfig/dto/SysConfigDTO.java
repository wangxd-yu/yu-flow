package org.yu.flow.module.sysconfig.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;

import java.time.LocalDateTime;

/**
 * 系统配置 DTO (API 响应)
 */
@Data
public class SysConfigDTO {

    private String id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型 (STRING / NUMBER / BOOLEAN / JSON) */
    private String valueType;

    /** 配置分组 */
    private String configGroup;

    /** 配置说明 */
    private String remark;

    /** 是否内置 (1: 内置, 0: 自定义) */
    private Integer isBuiltin;

    /** 状态 (1: 启用, 0: 停用) */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * DO → DTO
     */
    public static SysConfigDTO fromDO(SysConfigDO entity) {
        if (entity == null) return null;

        SysConfigDTO dto = new SysConfigDTO();
        dto.setId(entity.getId());
        dto.setConfigKey(entity.getConfigKey());
        dto.setConfigValue(entity.getConfigValue());
        dto.setValueType(entity.getValueType());
        dto.setConfigGroup(entity.getConfigGroup());
        dto.setRemark(entity.getRemark());
        dto.setIsBuiltin(entity.getIsBuiltin());
        dto.setStatus(entity.getStatus());
        dto.setCreateBy(entity.getCreateBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateBy(entity.getUpdateBy());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /**
     * DTO → DO（用于创建/更新时的转换）
     */
    public SysConfigDO toDO() {
        SysConfigDO entity = new SysConfigDO();
        entity.setConfigKey(this.configKey);
        entity.setConfigValue(this.configValue);
        entity.setValueType(this.valueType);
        entity.setConfigGroup(this.configGroup);
        entity.setRemark(this.remark);
        entity.setIsBuiltin(this.isBuiltin);
        entity.setStatus(this.status);
        return entity;
    }
}
