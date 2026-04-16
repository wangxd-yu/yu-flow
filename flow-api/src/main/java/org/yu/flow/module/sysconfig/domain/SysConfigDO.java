package org.yu.flow.module.sysconfig.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 系统配置表实体
 *
 * <p>用于动态管理诸如 SYSTEM_PREFIX、TOKEN_EXPIRE、OSS_CONFIG 等
 * 底层基础设施配置。配置以键值对形式存储，支持 STRING / NUMBER / BOOLEAN / JSON 四种值类型。</p>
 *
 * <h3>内置保护机制</h3>
 * <p>当 {@code isBuiltin = 1} 时，该配置项为系统内置参数，仅允许修改 configValue，
 * 禁止删除。这保证了平台核心配置的安全性。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow_sys_config")
public class SysConfigDO {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /**
     * 配置键 (唯一标识，如: SYSTEM_PREFIX, TOKEN_EXPIRE)
     */
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /**
     * 配置值 (支持字符串、数字、JSON 等格式)
     */
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /**
     * 值类型 (STRING / NUMBER / BOOLEAN / JSON)
     */
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    /**
     * 配置分组 (GENERAL / SECURITY / OSS / GATEWAY 等)
     */
    @Column(name = "config_group", nullable = false, length = 50)
    private String configGroup;

    /**
     * 配置说明
     */
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 是否内置 (1: 内置参数, 不允许删除; 0: 用户自定义)
     */
    @Column(name = "is_builtin", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Integer isBuiltin;

    /**
     * 状态 (1: 启用, 0: 停用)
     */
    @Column(name = "status", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Integer status;

    /** 创建者 */
    @Column(name = "create_by", length = 64)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** 更新者 */
    @Column(name = "update_by", length = 64)
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
