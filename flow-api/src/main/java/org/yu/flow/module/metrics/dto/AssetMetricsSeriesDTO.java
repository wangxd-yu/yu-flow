package org.yu.flow.module.metrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetMetricsSeriesDTO {
    private String assetType;
    private String assetId;
    private String window;
    /** minute | hour */
    private String granularity;
    private List<Point> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private String time;
        private long success;
        private long fail;
        private long skipped;
        private Long p95Ms;
    }
}
