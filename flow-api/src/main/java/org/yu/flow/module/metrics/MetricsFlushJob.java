package org.yu.flow.module.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.metrics.domain.FlowMetricsMetaDO;
import org.yu.flow.module.metrics.domain.FlowMetricsMinuteDO;
import org.yu.flow.module.metrics.repository.FlowMetricsMetaRepository;
import org.yu.flow.module.metrics.repository.FlowMetricsMinuteRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 将已闭合的 Redis 分钟桶刷入 MySQL（分布式锁，单节点执行）。
 */
@Slf4j
@Component
public class MetricsFlushJob {

    @Resource
    private YuFlowProperties yuFlowProperties;
    @Resource
    private FlowMetricsMinuteRepository metricsMinuteRepository;
    @Resource
    private FlowMetricsMetaRepository metricsMetaRepository;
    @Resource
    private ObjectMapper flowObjectMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        YuFlowProperties.Metrics cfg = cfg();
        int interval = cfg == null ? 30 : Math.max(10, cfg.getFlushIntervalSeconds());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-flush-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::safeFlush, interval, interval, TimeUnit.SECONDS);
        log.info("[MetricsFlushJob] 已启动，间隔 {}s", interval);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void safeFlush() {
        try {
            flushOnce();
        } catch (Exception e) {
            log.error("[MetricsFlushJob] flush 异常", e);
        }
    }

    public void flushOnce() {
        YuFlowProperties.Metrics cfg = cfg();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        String lockVal = UUID.randomUUID().toString();
        int lockTtl = Math.max(15, cfg.getFlushLockTtlSeconds());
        boolean locked;
        try {
            locked = FlowRedisUtil.setIfAbsent(MetricsKeys.FLUSH_LOCK, lockVal, lockTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[MetricsFlushJob] 抢锁失败（Redis）: {}", e.getMessage());
            return;
        }
        if (!locked) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                doFlushMinutes();
                doFlushMeta();
            });
        } finally {
            FlowRedisUtil.unlock(MetricsKeys.FLUSH_LOCK, lockVal);
        }
    }

    private void doFlushMinutes() {
        Set<Object> members = FlowRedisUtil.smembers(MetricsKeys.ACTIVE_SET);
        if (members == null || members.isEmpty()) {
            return;
        }
        LocalDateTime currentMinute = MetricsKeys.nowMinute();
        int flushed = 0;
        for (Object m : members) {
            if (m == null) {
                continue;
            }
            String key = String.valueOf(m);
            MetricsKeys.BucketKeyParts parts = MetricsKeys.parseBucketKey(key);
            if (parts == null) {
                FlowRedisUtil.srem(MetricsKeys.ACTIVE_SET, key);
                continue;
            }
            if (!parts.bucketStart().isBefore(currentMinute)) {
                continue; // 当前分钟未闭合
            }
            try {
                upsertFromRedis(parts);
                FlowRedisUtil.delete(key);
                FlowRedisUtil.srem(MetricsKeys.ACTIVE_SET, key);
                flushed++;
            } catch (Exception e) {
                log.warn("[MetricsFlushJob] 刷入分钟桶失败 key={}: {}", key, e.getMessage());
            }
        }
        if (flushed > 0) {
            log.debug("[MetricsFlushJob] 已刷入 {} 个分钟桶", flushed);
        }
    }

    private void doFlushMeta() {
        Set<Object> members = FlowRedisUtil.smembers(MetricsKeys.META_DIRTY_SET);
        if (members == null || members.isEmpty()) {
            return;
        }
        int flushed = 0;
        for (Object m : members) {
            if (m == null) {
                continue;
            }
            String key = String.valueOf(m);
            MetricsKeys.MetaKeyParts parts = MetricsKeys.parseMetaKey(key);
            if (parts == null) {
                FlowRedisUtil.srem(MetricsKeys.META_DIRTY_SET, key);
                continue;
            }
            try {
                if (upsertMetaFromRedis(parts)) {
                    flushed++;
                }
                FlowRedisUtil.srem(MetricsKeys.META_DIRTY_SET, key);
            } catch (Exception e) {
                log.warn("[MetricsFlushJob] 刷入 meta 失败 key={}: {}", key, e.getMessage());
            }
        }
        if (flushed > 0) {
            log.debug("[MetricsFlushJob] 已刷入 {} 条计量 meta", flushed);
        }
    }

    /** @return 是否实际写入 */
    private boolean upsertMetaFromRedis(MetricsKeys.MetaKeyParts parts) {
        Map<Object, Object> hash = FlowRedisUtil.hgetAll(parts.redisKey());
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        Long lastSuccess = readLongObj(hash, MetricsKeys.META_LAST_SUCCESS);
        Long lastFail = readLongObj(hash, MetricsKeys.META_LAST_FAIL);
        long consec = readLong(hash, MetricsKeys.META_CONSEC_FAIL);

        FlowMetricsMetaDO row = metricsMetaRepository
                .findByAssetTypeAndAssetId(parts.assetType().name(), parts.assetId())
                .orElse(null);
        if (row == null) {
            row = FlowMetricsMetaDO.builder()
                    .assetType(parts.assetType().name())
                    .assetId(parts.assetId())
                    .lastSuccessAt(lastSuccess)
                    .lastFailAt(lastFail)
                    .consecFail(consec)
                    .build();
        } else {
            // Redis 字段可能因 TTL 重建后缺失；非空才覆盖，避免冲掉已落库的 last*
            if (lastSuccess != null) {
                row.setLastSuccessAt(lastSuccess);
            }
            if (lastFail != null) {
                row.setLastFailAt(lastFail);
            }
            row.setConsecFail(consec);
        }
        metricsMetaRepository.save(row);
        return true;
    }

    private void upsertFromRedis(MetricsKeys.BucketKeyParts parts) {
        Map<Object, Object> hash = FlowRedisUtil.hgetAll(parts.redisKey());
        if (hash == null || hash.isEmpty()) {
            return;
        }
        long success = readLong(hash, MetricsKeys.FIELD_SUCCESS);
        long fail = readLong(hash, MetricsKeys.FIELD_FAIL);
        long skipped = readLong(hash, MetricsKeys.FIELD_SKIPPED);
        long authFail = readLong(hash, MetricsKeys.FIELD_AUTH_FAIL);
        long sumCost = readLong(hash, MetricsKeys.FIELD_SUM_COST);
        long latencyCount = readLong(hash, MetricsKeys.FIELD_LATENCY_COUNT);
        long[] hist = LatencyHistogram.empty();
        for (int i = 0; i < LatencyHistogram.BUCKET_COUNT; i++) {
            hist[i] = readLong(hash, MetricsKeys.histField(i));
        }

        LocalDateTime bucketStart = parts.bucketStart();
        FlowMetricsMinuteDO row = metricsMinuteRepository
                .findByAssetTypeAndAssetIdAndTriggerTypeAndBucketStart(
                        parts.assetType().name(), parts.assetId(), parts.triggerType(), bucketStart)
                .orElse(null);

        if (row == null) {
            row = FlowMetricsMinuteDO.builder()
                    .assetType(parts.assetType().name())
                    .assetId(parts.assetId())
                    .triggerType(parts.triggerType())
                    .bucketStart(bucketStart)
                    .successCnt(success)
                    .failCnt(fail)
                    .authFailCnt(authFail)
                    .skippedCnt(skipped)
                    .sumCostMs(sumCost)
                    .latencyCount(latencyCount)
                    .histJson(LatencyHistogram.toJson(hist, flowObjectMapper))
                    .build();
        } else {
            // Redis 桶是该分钟完整视图；以 Redis 覆盖（单热源），避免重复 flush 双加
            row.setSuccessCnt(success);
            row.setFailCnt(fail);
            row.setAuthFailCnt(authFail);
            row.setSkippedCnt(skipped);
            row.setSumCostMs(sumCost);
            row.setLatencyCount(latencyCount);
            row.setHistJson(LatencyHistogram.toJson(hist, flowObjectMapper));
        }
        metricsMinuteRepository.save(row);
    }

    private static long readLong(Map<Object, Object> hash, String field) {
        Long v = readLongObj(hash, field);
        return v == null ? 0L : v;
    }

    private static Long readLongObj(Map<Object, Object> hash, String field) {
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
            return null;
        }
        try {
            String s = String.valueOf(v).replace("\"", "");
            if (s.isBlank() || "null".equalsIgnoreCase(s)) {
                return null;
            }
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private YuFlowProperties.Metrics cfg() {
        return yuFlowProperties == null ? null : yuFlowProperties.getMetrics();
    }
}
