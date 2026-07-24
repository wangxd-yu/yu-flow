package org.yu.flow.module.sysconfig.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 系统配置 DTO (API 响应)
 */
@Data
public class SysConfigDTO {

    public static final String MASKED_VALUE = "***";

    private String id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型 (STRING / NUMBER / BOOLEAN / JSON / ENUM) */
    private String valueType;

    /** 配置分组 */
    private String configGroup;

    /** 配置说明 */
    private String remark;

    /** 是否内置 (1: 内置, 0: 自定义) */
    private Integer isBuiltin;

    /** 状态 (1: 启用, 0: 停用) */
    private Integer status;

    /** 组内展示顺序，越小越靠前 */
    private Integer sortOrder;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 是否应按密钥类字段脱敏（PASSWORD / SECRET / WEBHOOK / TOKEN / API_KEY）。
     */
    public static boolean isSecretConfigKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return false;
        }
        String k = configKey.toUpperCase(Locale.ROOT);
        return k.contains("PASSWORD")
                || k.contains("SECRET")
                || k.contains("WEBHOOK")
                || k.contains("TOKEN")
                || k.contains("API_KEY")
                || k.endsWith("_CREDENTIAL")
                || k.endsWith("_CREDENTIALS");
    }

    /** 读接口脱敏：密钥类返回 {@link #MASKED_VALUE} */
    public static String maskIfSecret(String configKey, String configValue) {
        return isSecretConfigKey(configKey) ? MASKED_VALUE : configValue;
    }

    /**
     * DO → DTO（密钥类 configValue 脱敏）
     */
    public static SysConfigDTO fromDO(SysConfigDO entity) {
        if (entity == null) return null;

        SysConfigDTO dto = new SysConfigDTO();
        dto.setId(entity.getId());
        dto.setConfigKey(entity.getConfigKey());
        dto.setConfigValue(maskIfSecret(entity.getConfigKey(), entity.getConfigValue()));
        dto.setValueType(entity.getValueType());
        dto.setConfigGroup(entity.getConfigGroup());
        dto.setRemark(entity.getRemark());
        dto.setIsBuiltin(entity.getIsBuiltin());
        dto.setStatus(entity.getStatus());
        dto.setSortOrder(entity.getSortOrder());
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
        entity.setSortOrder(this.sortOrder != null ? this.sortOrder : 100);
        return entity;
    }
}
