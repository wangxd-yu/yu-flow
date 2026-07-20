package org.yu.flow.module.assetversion.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资产发布历史版本实体（表 flow_asset_version）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_asset_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_biz_asset_ver",
                columnNames = {"biz_type", "asset_id", "version_no"}))
public class FlowAssetVersionDO implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** api / task / service */
    @Column(name = "biz_type", nullable = false, length = 16)
    private String bizType;

    @Column(name = "asset_id", nullable = false, length = 32)
    private String assetId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "snapshot", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String snapshot;

    /** publish / rollback */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "publisher", length = 64)
    private String publisher;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "publish_time", nullable = false)
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
