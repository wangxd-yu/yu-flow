package org.yu.flow.module.envvar.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 环境变量（表 flow_sys_env_variable）。
 *
 * <p>每个环境各自维护一份，值不随发布包迁移；编排里用 {@code $.env.CODE} / {@code ${env.CODE}} 引用。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_sys_env_variable")
public class SysEnvVariableDO {

    @Id
    @Column(name = "id", nullable = false, length = 32)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 变量名，大写字母开头，仅大写字母 / 数字 / 下划线 */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** 变量值；secret=true 时为 AES 密文 */
    @Column(name = "var_value", length = 4000)
    private String value;

    /** 敏感变量：页面只显示掩码，执行轨迹与三方日志落库前脱敏 */
    @Column(name = "secret", nullable = false)
    private Boolean secret;

    @Column(name = "remark", length = 512)
    private String remark;

    @Column(name = "create_by", length = 64)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_by", length = 64)
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
