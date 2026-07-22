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
        name = "flow_metrics_minute",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_flow_metrics_minute_asset_bucket",
                columnNames = {"asset_type", "asset_id", "trigger_type", "bucket_start"}
        )
)
public class FlowMetricsMinuteDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "asset_type", nullable = false, length = 16)
    private String assetType;

    @Column(name = "asset_id", nullable = false, length = 32)
    private String assetId;

    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "bucket_start", nullable = false)
    private Date bucketStart;

    @Column(name = "success_cnt", nullable = false)
    private Long successCnt;

    @Column(name = "fail_cnt", nullable = false)
    private Long failCnt;

    @Column(name = "skipped_cnt", nullable = false)
    private Long skippedCnt;

    @Column(name = "sum_cost_ms", nullable = false)
    private Long sumCostMs;

    @Column(name = "latency_count", nullable = false)
    private Long latencyCount;

    @Column(name = "hist_json", length = 1024)
    private String histJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private Date updateTime;

    @PrePersist
    @PreUpdate
    public void touch() {
        updateTime = new Date();
        if (successCnt == null) successCnt = 0L;
        if (failCnt == null) failCnt = 0L;
        if (skippedCnt == null) skippedCnt = 0L;
        if (sumCostMs == null) sumCostMs = 0L;
        if (latencyCount == null) latencyCount = 0L;
        if (triggerType == null) triggerType = "_";
    }
}
