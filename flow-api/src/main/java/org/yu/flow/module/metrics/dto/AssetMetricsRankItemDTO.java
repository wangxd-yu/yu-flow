package org.yu.flow.module.metrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetMetricsRankItemDTO {
    private String assetType;
    private String assetId;
    private String assetName;
    private long totalCalls;
    private long successCount;
    private long failCount;
    private Double successRate;
    private Double errorRate;
    private Long p95Ms;
    /** 当前连续失败（现值，任何一次成功即清零；用于健康度判定） */
    private long consecutiveFail;
    /** 窗口内最大连续失败（分钟桶推导近似；用于排行展示） */
    private long maxConsecutiveFail;
    private String health;
}
