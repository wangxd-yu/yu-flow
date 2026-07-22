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
    private long consecutiveFail;
    private String health;
}
