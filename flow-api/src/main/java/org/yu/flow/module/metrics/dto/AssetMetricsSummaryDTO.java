package org.yu.flow.module.metrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetMetricsSummaryDTO {
    private String assetType;
    private String assetId;
    private String window;
    private long totalCalls;
    private long successCount;
    private long failCount;
    private long skippedCount;
    /** 鉴权失败（不计入成功率分母） */
    private long authFailCount;
    /** null = 时间窗内无终态样本 */
    private Double successRate;
    private Double errorRate;
    private Double avgCostMs;
    private Long p50Ms;
    private Long p95Ms;
    private Long p99Ms;
    private Long lastSuccessAt;
    private Long lastFailAt;
    private long consecutiveFail;
}
