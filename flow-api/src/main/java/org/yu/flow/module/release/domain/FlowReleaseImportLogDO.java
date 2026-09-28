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
 * 发布包导入记录（表 flow_log_release_import），含导入前备份，用于一键回滚。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_log_release_import")
public class FlowReleaseImportLogDO {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

    @Id
    @Column(name = "id", nullable = false, length = 32)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "release_code", length = 64)
    private String releaseCode;

    @Column(name = "release_name", length = 128)
    private String releaseName;

    /** 发布包 manifest SHA-256，可与来源环境版本单的 package_digest 对账 */
    @Column(name = "package_digest", length = 64)
    private String packageDigest;

    @Column(name = "source_env", length = 32)
    private String sourceEnv;

    @Column(name = "target_env", length = 32)
    private String targetEnv;

    /** SUCCESS / FAILED / ROLLED_BACK */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 一句话摘要，如「新增 3 / 更新 5 / 发布 7」 */
    @Column(name = "summary", length = 512)
    private String summary;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** 导入报告 JSON（逐项动作、依赖检查） */
    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    /** 导入前受影响资产的完整状态 JSON，回滚依据 */
    @Column(name = "backup_json", columnDefinition = "TEXT")
    private String backupJson;

    /** 导入完成后各资产的内容指纹（类型:ID → 指纹），下次导入据此发现生产被直接修改过的资产 */
    @Column(name = "asset_hashes", columnDefinition = "TEXT")
    private String assetHashes;

    /** 导入提交后的运行时自检问题（JSON 字符串数组），如 MQ 订阅失败；为空表示自检通过 */
    @Column(name = "runtime_issues", columnDefinition = "TEXT")
    private String runtimeIssues;

    @Column(name = "imported_by", length = 64)
    private String importedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "imported_time")
    private LocalDateTime importedTime;

    @Column(name = "rolled_back_by", length = 64)
    private String rolledBackBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "rolled_back_time")
    private LocalDateTime rolledBackTime;
}
