package org.yu.flow.module.metrics.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "flow_metrics_meta",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_flow_metrics_meta_asset",
                columnNames = {"asset_type", "asset_id"}
        )
)
public class FlowMetricsMetaDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "asset_type", nullable = false, length = 16)
    private String assetType;

    @Column(name = "asset_id", nullable = false, length = 32)
    private String assetId;

    /** 最近成功 epoch ms */
    @Column(name = "last_success_at")
    private Long lastSuccessAt;

    /** 最近业务失败 epoch ms */
    @Column(name = "last_fail_at")
    private Long lastFailAt;

    @Column(name = "consec_fail", nullable = false)
    private Long consecFail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private Date updateTime;

    @PrePersist
    @PreUpdate
    public void touch() {
        updateTime = new Date();
        if (consecFail == null) {
            consecFail = 0L;
        }
    }
}
