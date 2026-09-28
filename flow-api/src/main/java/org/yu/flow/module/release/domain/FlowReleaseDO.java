package org.yu.flow.module.release.domain;

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
 * 版本单（表 flow_release）：一轮上线要带到生产的资产清单。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_release")
public class FlowReleaseDO {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_EXPORTED = "EXPORTED";

    @Id
    @Column(name = "id", nullable = false, length = 32)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 版本号，全局唯一，如 v2026.10 */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", length = 128)
    private String name;

    /** DRAFT 编辑中 / FROZEN 已冻结 / EXPORTED 已导出 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 发布说明，导出时写进包内 CHANGELOG.md */
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    /** 创建时的实例环境 */
    @Column(name = "source_env", length = 32)
    private String sourceEnv;

    @Column(name = "frozen_by", length = 64)
    private String frozenBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "frozen_time")
    private LocalDateTime frozenTime;

    @Column(name = "exported_by", length = 64)
    private String exportedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "exported_time")
    private LocalDateTime exportedTime;

    /** 最近一次导出包的 manifest SHA-256，用于和生产导入记录对账 */
    @Column(name = "package_digest", length = 64)
    private String packageDigest;

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
