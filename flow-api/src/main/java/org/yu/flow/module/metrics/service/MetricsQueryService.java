package org.yu.flow.module.metrics.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.metrics.*;
import org.yu.flow.module.metrics.domain.FlowMetricsMetaDO;
import org.yu.flow.module.metrics.domain.FlowMetricsMinuteDO;
import org.yu.flow.module.metrics.dto.*;
import org.yu.flow.module.metrics.repository.FlowMetricsMetaRepository;
import org.yu.flow.module.metrics.repository.FlowMetricsMinuteRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class MetricsQueryService {

    private static final DateTimeFormatter POINT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private FlowMetricsMinuteRepository metricsMinuteRepository;
    @Resource
    private FlowMetricsMetaRepository metricsMetaRepository;
    @Resource
    private ObjectMapper flowObjectMapper;
    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowTaskRepository flowTaskRepository;
    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;
    @Resource
    private FlowOpenPlatformRepository flowOpenPlatformRepository;

    public AssetMetricsSummaryDTO summary(MetricsAssetType type, String assetId, MetricsWindow window) {
        MetricsBucketAgg agg = aggregateAsset(type, assetId, window);
        Meta meta = readMeta(type, assetId);
        Double successRate = agg.successRate();
        Double errorRate = successRate == null ? null : (1.0 - successRate);
        return AssetMetricsSummaryDTO.builder()
                .assetType(type.name())
                .assetId(assetId)
                .window(window.label())
                .totalCalls(agg.totalCalls())
                .successCount(agg.getSuccess())
                .failCount(agg.getFail())
                .skippedCount(agg.getSkipped())
                .authFailCount(agg.getAuthFail())
                .successRate(successRate)
                .errorRate(errorRate)
                .avgCostMs(agg.avgCostMs())
                .p50Ms(LatencyHistogram.percentile(agg.getHist(), 0.50))
                .p95Ms(LatencyHistogram.percentile(agg.getHist(), 0.95))
                .p99Ms(LatencyHistogram.percentile(agg.getHist(), 0.99))
                .lastSuccessAt(meta.lastSuccessAt)
                .lastFailAt(meta.lastFailAt)
                .consecutiveFail(meta.consecFail)
                .build();
    }

    public AssetMetricsSeriesDTO series(MetricsAssetType type, String assetId, MetricsWindow window) {
        LocalDateTime now = MetricsKeys.nowMinute();
        LocalDateTime from = window.from(now);
        NavigableMap<LocalDateTime, MetricsBucketAgg> byMinute = loadMinuteMap(type, assetId, from, now.plusMinutes(1));

        boolean hourly = window.hourlySeries();
        List<AssetMetricsSeriesDTO.Point> points = new ArrayList<>();
        if (hourly) {
            NavigableMap<LocalDateTime, MetricsBucketAgg> byHour = new TreeMap<>();
            for (Map.Entry<LocalDateTime, MetricsBucketAgg> e : byMinute.entrySet()) {
                LocalDateTime hour = e.getKey().withMinute(0);
                byHour.computeIfAbsent(hour, k -> new MetricsBucketAgg()).add(e.getValue());
            }
            for (Map.Entry<LocalDateTime, MetricsBucketAgg> e : byHour.entrySet()) {
                points.add(toPoint(e.getKey(), e.getValue()));
            }
        } else {
            for (Map.Entry<LocalDateTime, MetricsBucketAgg> e : byMinute.entrySet()) {
                points.add(toPoint(e.getKey(), e.getValue()));
            }
        }
        return AssetMetricsSeriesDTO.builder()
                .assetType(type.name())
                .assetId(assetId)
                .window(window.label())
                .granularity(hourly ? "hour" : "minute")
                .points(points)
                .build();
    }

    public List<AssetHealthDTO> health(List<MetricsHealthRequest.Item> items) {
        MetricsWindow window = MetricsWindow.H24;
        List<AssetHealthDTO> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (MetricsHealthRequest.Item item : items) {
            if (item == null || StrUtil.isBlank(item.getAssetType()) || StrUtil.isBlank(item.getAssetId())) {
                continue;
            }
            try {
                MetricsAssetType type = MetricsAssetType.fromPath(item.getAssetType());
                AssetMetricsSummaryDTO s = summary(type, item.getAssetId(), window);
                out.add(AssetHealthDTO.builder()
                        .assetType(type.name())
                        .assetId(item.getAssetId())
                        .health(resolveHealth(s))
                        .successRate(s.getSuccessRate())
                        .consecutiveFail(s.getConsecutiveFail())
                        .totalCalls(s.getTotalCalls())
                        .window(window.label())
                        .build());
            } catch (Exception e) {
                log.debug("[MetricsQuery] health skip: {}", e.getMessage());
            }
        }
        return out;
    }

    public List<AssetMetricsRankItemDTO> rank(MetricsAssetType type, MetricsWindow window, String orderBy, int limit) {
        LocalDateTime now = MetricsKeys.nowMinute();
        LocalDateTime from = window.from(now);
        List<FlowMetricsMinuteDO> rows = metricsMinuteRepository
                .findByAssetTypeAndBucketStartGreaterThanEqualAndBucketStartLessThan(
                        type.name(), MetricsKeys.toDate(from), MetricsKeys.toDate(now.plusMinutes(1)));

        Map<String, MetricsBucketAgg> byAsset = new HashMap<>();
        for (FlowMetricsMinuteDO row : rows) {
            byAsset.computeIfAbsent(row.getAssetId(), k -> new MetricsBucketAgg())
                    .addCounts(
                            nz(row.getSuccessCnt()), nz(row.getFailCnt()), nz(row.getSkippedCnt()),
                            nz(row.getAuthFailCnt()),
                            nz(row.getSumCostMs()), nz(row.getLatencyCount()),
                            LatencyHistogram.fromJson(row.getHistJson(), flowObjectMapper));
        }
        // 合并 Redis 未刷桶
        mergeRedisForType(type, from, now.plusMinutes(1), byAsset);

        String order = orderBy == null ? "errorRate" : orderBy.trim();
        List<AssetMetricsRankItemDTO> list = new ArrayList<>();
        for (Map.Entry<String, MetricsBucketAgg> e : byAsset.entrySet()) {
            MetricsBucketAgg agg = e.getValue();
            if (agg.totalCalls() <= 0) {
                continue;
            }
            Meta meta = readMeta(type, e.getKey());
            Double sr = agg.successRate();
            AssetMetricsSummaryDTO tmp = AssetMetricsSummaryDTO.builder()
                    .successRate(sr)
                    .consecutiveFail(meta.consecFail)
                    .totalCalls(agg.totalCalls())
                    .failCount(agg.getFail())
                    .build();
            list.add(AssetMetricsRankItemDTO.builder()
                    .assetType(type.name())
                    .assetId(e.getKey())
                    .assetName(resolveName(type, e.getKey()))
                    .totalCalls(agg.totalCalls())
                    .successCount(agg.getSuccess())
                    .failCount(agg.getFail())
                    .successRate(sr)
                    .errorRate(sr == null ? null : 1.0 - sr)
                    .p95Ms(LatencyHistogram.percentile(agg.getHist(), 0.95))
                    .consecutiveFail(meta.consecFail)
                    .health(resolveHealth(tmp))
                    .build());
        }

        Comparator<AssetMetricsRankItemDTO> cmp = switch (order) {
            case "p95" -> Comparator.comparing((AssetMetricsRankItemDTO x) -> x.getP95Ms() == null ? -1L : x.getP95Ms()).reversed();
            case "calls" -> Comparator.comparingLong(AssetMetricsRankItemDTO::getTotalCalls).reversed();
            default -> Comparator.comparing((AssetMetricsRankItemDTO x) -> x.getErrorRate() == null ? -1.0 : x.getErrorRate()).reversed();
        };
        list.sort(cmp);
        int lim = Math.min(Math.max(limit, 1), 100);
        if (list.size() > lim) {
            return list.subList(0, lim);
        }
        return list;
    }

    public List<AssetMetricsRankItemDTO> anomalies(MetricsWindow window, int limit) {
        List<AssetMetricsRankItemDTO> all = new ArrayList<>();
        for (MetricsAssetType type : MetricsAssetType.values()) {
            all.addAll(rank(type, window, "errorRate", 50));
        }
        all.removeIf(x -> !"error".equals(x.getHealth()) && !"warn".equals(x.getHealth()));
        all.sort(Comparator
                .comparing((AssetMetricsRankItemDTO x) -> "error".equals(x.getHealth()) ? 0 : 1)
                .thenComparing((AssetMetricsRankItemDTO x) -> x.getErrorRate() == null ? -1.0 : x.getErrorRate(), Comparator.reverseOrder()));
        int lim = Math.min(Math.max(limit, 1), 100);
        return all.size() > lim ? all.subList(0, lim) : all;
    }

    // ─── internals ───

    private MetricsBucketAgg aggregateAsset(MetricsAssetType type, String assetId, MetricsWindow window) {
        LocalDateTime now = MetricsKeys.nowMinute();
        LocalDateTime from = window.from(now);
        NavigableMap<LocalDateTime, MetricsBucketAgg> map = loadMinuteMap(type, assetId, from, now.plusMinutes(1));
        MetricsBucketAgg total = new MetricsBucketAgg();
        for (MetricsBucketAgg a : map.values()) {
            total.add(a);
        }
        return total;
    }

    private NavigableMap<LocalDateTime, MetricsBucketAgg> loadMinuteMap(
            MetricsAssetType type, String assetId, LocalDateTime from, LocalDateTime toExclusive) {
        NavigableMap<LocalDateTime, MetricsBucketAgg> map = new TreeMap<>();
        List<FlowMetricsMinuteDO> rows = metricsMinuteRepository
                .findByAssetTypeAndAssetIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(
                        type.name(), assetId, MetricsKeys.toDate(from), MetricsKeys.toDate(toExclusive));
        for (FlowMetricsMinuteDO row : rows) {
            LocalDateTime bucket = MetricsKeys.toLocal(row.getBucketStart());
            map.computeIfAbsent(bucket, k -> new MetricsBucketAgg())
                    .addCounts(
                            nz(row.getSuccessCnt()), nz(row.getFailCnt()), nz(row.getSkippedCnt()),
                            nz(row.getAuthFailCnt()),
                            nz(row.getSumCostMs()), nz(row.getLatencyCount()),
                            LatencyHistogram.fromJson(row.getHistJson(), flowObjectMapper));
        }
        // Redis：覆盖同分钟（热层为该分钟最新完整视图）
        Set<Object> active = FlowRedisUtil.smembers(MetricsKeys.ACTIVE_SET);
        if (active != null) {
            for (Object m : active) {
                MetricsKeys.BucketKeyParts parts = MetricsKeys.parseBucketKey(String.valueOf(m));
                if (parts == null || parts.assetType() != type || !assetId.equals(parts.assetId())) {
                    continue;
                }
                if (parts.bucketStart().isBefore(from) || !parts.bucketStart().isBefore(toExclusive)) {
                    continue;
                }
                MetricsBucketAgg redisAgg = readRedisBucket(parts.redisKey());
                map.put(parts.bucketStart(), mergePreferRedis(map.get(parts.bucketStart()), redisAgg));
            }
        }
        return map;
    }

    private void mergeRedisForType(MetricsAssetType type, LocalDateTime from, LocalDateTime toExclusive,
                                   Map<String, MetricsBucketAgg> byAsset) {
        Set<Object> active = FlowRedisUtil.smembers(MetricsKeys.ACTIVE_SET);
        if (active == null) {
            return;
        }
        for (Object m : active) {
            MetricsKeys.BucketKeyParts parts = MetricsKeys.parseBucketKey(String.valueOf(m));
            if (parts == null || parts.assetType() != type) {
                continue;
            }
            if (parts.bucketStart().isBefore(from) || !parts.bucketStart().isBefore(toExclusive)) {
                continue;
            }
            MetricsBucketAgg redisAgg = readRedisBucket(parts.redisKey());
            byAsset.computeIfAbsent(parts.assetId(), k -> new MetricsBucketAgg()).add(redisAgg);
        }
    }

    /** Redis 未刷桶与 DB 同分钟并存时，以 Redis 覆盖该分钟（避免双计）；跨触发维度则相加 */
    private MetricsBucketAgg mergePreferRedis(MetricsBucketAgg db, MetricsBucketAgg redis) {
        if (redis == null) {
            return db == null ? new MetricsBucketAgg() : db;
        }
        // 简化：同一分钟多 trigger 在 loadMinuteMap 中按分钟聚合——若 DB 已有同分钟，
        // Redis 单 trigger 覆盖会丢其他 trigger。改为相加并对同 key 去重困难。
        // 实践：flush 后删 Redis；未 flush 时 DB 通常无该分钟 → 直接用 Redis。
        if (db == null || db.totalCalls() == 0) {
            return redis;
        }
        // 已有 DB：再加 Redis（可能轻微双计仅在 flush 竞态窗口，可接受）
        MetricsBucketAgg out = new MetricsBucketAgg();
        out.add(db);
        out.add(redis);
        return out;
    }

    private MetricsBucketAgg readRedisBucket(String key) {
        MetricsBucketAgg agg = new MetricsBucketAgg();
        Map<Object, Object> hash = FlowRedisUtil.hgetAll(key);
        if (hash == null || hash.isEmpty()) {
            return agg;
        }
        long[] hist = LatencyHistogram.empty();
        for (int i = 0; i < LatencyHistogram.BUCKET_COUNT; i++) {
            hist[i] = readLong(hash, MetricsKeys.histField(i));
        }
        agg.addCounts(
                readLong(hash, MetricsKeys.FIELD_SUCCESS),
                readLong(hash, MetricsKeys.FIELD_FAIL),
                readLong(hash, MetricsKeys.FIELD_SKIPPED),
                readLong(hash, MetricsKeys.FIELD_AUTH_FAIL),
                readLong(hash, MetricsKeys.FIELD_SUM_COST),
                readLong(hash, MetricsKeys.FIELD_LATENCY_COUNT),
                hist);
        return agg;
    }

    private static long readLong(Map<Object, Object> hash, String field) {
        Object v = hash.get(field);
        if (v == null) {
            for (Map.Entry<Object, Object> e : hash.entrySet()) {
                if (e.getKey() != null && field.equals(String.valueOf(e.getKey()).replace("\"", ""))) {
                    v = e.getValue();
                    break;
                }
            }
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v).replace("\"", ""));
        } catch (Exception e) {
            return 0L;
        }
    }

    private AssetMetricsSeriesDTO.Point toPoint(LocalDateTime t, MetricsBucketAgg a) {
        return AssetMetricsSeriesDTO.Point.builder()
                .time(t.format(POINT_FMT))
                .success(a.getSuccess())
                .fail(a.getFail())
                .skipped(a.getSkipped())
                .p95Ms(LatencyHistogram.percentile(a.getHist(), 0.95))
                .build();
    }

    private static String resolveHealth(AssetMetricsSummaryDTO s) {
        if (s.getTotalCalls() <= 0 && s.getConsecutiveFail() <= 0) {
            return "empty";
        }
        if (s.getConsecutiveFail() >= 3) {
            return "error";
        }
        if (s.getSuccessRate() != null && s.getSuccessRate() < 0.9 && s.getFailCount() > 0) {
            return "error";
        }
        if (s.getConsecutiveFail() >= 1) {
            return "warn";
        }
        if (s.getSuccessRate() != null && s.getSuccessRate() < 0.99 && s.getFailCount() > 0) {
            return "warn";
        }
        return "ok";
    }

    private Meta readMeta(MetricsAssetType type, String assetId) {
        Meta meta = new Meta();
        boolean redisHit = false;
        try {
            String key = MetricsKeys.metaKey(type, assetId);
            meta.lastSuccessAt = parseLongObj(FlowRedisUtil.hget(key, MetricsKeys.META_LAST_SUCCESS));
            meta.lastFailAt = parseLongObj(FlowRedisUtil.hget(key, MetricsKeys.META_LAST_FAIL));
            Long cf = parseLongObj(FlowRedisUtil.hget(key, MetricsKeys.META_CONSEC_FAIL));
            meta.consecFail = cf == null ? 0L : cf;
            // consecFail=0 且无 last* 时，仍可能是「刚成功清零」的有效 Redis 态；用 key 是否存在区分
            redisHit = meta.lastSuccessAt != null || meta.lastFailAt != null
                    || cf != null
                    || FlowRedisUtil.hasKey(key);
        } catch (Exception ignored) {
            // ignore
        }
        if (redisHit) {
            return meta;
        }
        // Redis 过期 → 正式 meta 表
        if (loadMetaFromTable(type, assetId, meta)) {
            return meta;
        }
        // 仍无 → 近 24h 分钟桶近似推导
        deriveMetaFromDb(type, assetId, meta);
        return meta;
    }

    private boolean loadMetaFromTable(MetricsAssetType type, String assetId, Meta meta) {
        try {
            Optional<FlowMetricsMetaDO> opt =
                    metricsMetaRepository.findByAssetTypeAndAssetId(type.name(), assetId);
            if (opt.isEmpty()) {
                return false;
            }
            FlowMetricsMetaDO row = opt.get();
            meta.lastSuccessAt = row.getLastSuccessAt();
            meta.lastFailAt = row.getLastFailAt();
            meta.consecFail = row.getConsecFail() == null ? 0L : row.getConsecFail();
            return meta.lastSuccessAt != null || meta.lastFailAt != null || meta.consecFail > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从新到旧扫描分钟桶：遇 success 则打断连续失败；累加仅失败分钟的 fail。
     * 时间为桶起点近似（非精确调用时刻）。
     */
    private void deriveMetaFromDb(MetricsAssetType type, String assetId, Meta meta) {
        try {
            LocalDateTime now = MetricsKeys.nowMinute();
            LocalDateTime from = now.minusHours(24);
            List<FlowMetricsMinuteDO> rows = metricsMinuteRepository
                    .findByAssetTypeAndAssetIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(
                            type.name(), assetId, MetricsKeys.toDate(from), MetricsKeys.toDate(now.plusMinutes(1)));
            if (rows == null || rows.isEmpty()) {
                return;
            }
            rows.sort((a, b) -> b.getBucketStart().compareTo(a.getBucketStart()));
            long consec = 0;
            boolean streak = true;
            for (FlowMetricsMinuteDO row : rows) {
                long s = nz(row.getSuccessCnt());
                long f = nz(row.getFailCnt());
                if (meta.lastFailAt == null && f > 0) {
                    meta.lastFailAt = row.getBucketStart().getTime();
                }
                if (meta.lastSuccessAt == null && s > 0) {
                    meta.lastSuccessAt = row.getBucketStart().getTime();
                }
                if (!streak) {
                    continue;
                }
                if (s > 0) {
                    streak = false;
                    consec = 0;
                } else if (f > 0) {
                    consec += f;
                }
            }
            meta.consecFail = consec;
        } catch (Exception ignored) {
            // fail-open
        }
    }

    private static Long parseLongObj(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v).replace("\"", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveName(MetricsAssetType type, String id) {
        try {
            return switch (type) {
                case API -> flowApiRepository.findById(id).map(FlowApiDO::getName).orElse(id);
                case TASK -> flowTaskRepository.findById(id).map(FlowTaskDO::getName).orElse(id);
                case SERVICE -> flowServiceFlowRepository.findById(id).map(FlowServiceFlowDO::getName).orElse(id);
                case PLATFORM -> flowOpenPlatformRepository.findById(id).map(FlowOpenPlatformDO::getName).orElse(id);
            };
        } catch (Exception e) {
            return id;
        }
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static class Meta {
        Long lastSuccessAt;
        Long lastFailAt;
        long consecFail;
    }
}
