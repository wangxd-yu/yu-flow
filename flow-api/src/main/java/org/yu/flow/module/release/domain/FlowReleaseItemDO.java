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
 * 版本单明细（表 flow_release_item）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_release_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_flow_release_item_release_id_atype_aid",
                columnNames = {"release_id", "asset_type", "asset_id"}))
public class FlowReleaseItemDO {

    public static final String ACTION_UPSERT = "UPSERT";
    /** 目标环境撤销发布 / 停用，不物理删除 */
    public static final String ACTION_OFFLINE = "OFFLINE";

    public static final String ORIGIN_MANUAL = "MANUAL";
    public static final String ORIGIN_DEPENDENCY = "DEPENDENCY";
    public static final String ORIGIN_SCAN = "SCAN";

    @Id
    @Column(name = "id", nullable = false, length = 32)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "release_id", nullable = false, length = 32)
    private String releaseId;

    /** API / SERVICE / TASK / MQ_TASK / RESPONSE_TEMPLATE */
    @Column(name = "asset_type", nullable = false, length = 32)
    private String assetType;

    @Column(name = "asset_id", nullable = false, length = 64)
    private String assetId;

    /** 冗余名称，资产被删后仍可显示 */
    @Column(name = "asset_name", length = 255)
    private String assetName;

    /** 按编码匹配的类型（全局宏 / 系统配置 / 开放平台）在目标环境的匹配键 */
    @Column(name = "asset_key", length = 128)
    private String assetKey;

    /** UPSERT 新增或更新 / OFFLINE 下线 */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    /** MANUAL 手工加入 / DEPENDENCY 依赖自动补齐 / SCAN 变更扫描 */
    @Column(name = "origin", nullable = false, length = 16)
    private String origin;

    /** 冻结时的内容指纹；冻结后内容再变即视为漂移 */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "create_by", length = 64)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
