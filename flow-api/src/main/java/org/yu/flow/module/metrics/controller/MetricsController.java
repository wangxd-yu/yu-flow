package org.yu.flow.module.metrics.controller;

import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsWindow;
import org.yu.flow.module.metrics.dto.*;
import org.yu.flow.module.metrics.service.MetricsQueryService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/metrics")
public class MetricsController {

    @Resource
    private MetricsQueryService metricsQueryService;

    @GetMapping("/{assetType}/{assetId}/summary")
    public R<AssetMetricsSummaryDTO> summary(
            @PathVariable String assetType,
            @PathVariable String assetId,
            @RequestParam(value = "window", required = false, defaultValue = "24h") String window) {
        return R.ok(metricsQueryService.summary(
                MetricsAssetType.fromPath(assetType), assetId, MetricsWindow.fromParam(window)));
    }

    @GetMapping("/{assetType}/{assetId}/series")
    public R<AssetMetricsSeriesDTO> series(
            @PathVariable String assetType,
            @PathVariable String assetId,
            @RequestParam(value = "window", required = false, defaultValue = "24h") String window) {
        return R.ok(metricsQueryService.series(
                MetricsAssetType.fromPath(assetType), assetId, MetricsWindow.fromParam(window)));
    }

    @PostMapping(
            value = "/health",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/json;charset=UTF-8"})
    public R<List<AssetHealthDTO>> health(
            @RequestBody(required = false) MetricsHealthRequest request) {
        return R.ok(metricsQueryService.health(request == null ? null : request.getItems()));
    }

    @GetMapping("/rank")
    public R<List<AssetMetricsRankItemDTO>> rank(
            @RequestParam String assetType,
            @RequestParam(value = "window", required = false, defaultValue = "24h") String window,
            @RequestParam(value = "orderBy", required = false, defaultValue = "errorRate") String orderBy,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        return R.ok(metricsQueryService.rank(
                MetricsAssetType.fromPath(assetType),
                MetricsWindow.fromParam(window),
                orderBy,
                limit));
    }

    @GetMapping("/anomalies")
    public R<List<AssetMetricsRankItemDTO>> anomalies(
            @RequestParam(value = "window", required = false, defaultValue = "24h") String window,
            @RequestParam(value = "limit", required = false, defaultValue = "30") int limit) {
        return R.ok(metricsQueryService.anomalies(MetricsWindow.fromParam(window), limit));
    }
}
