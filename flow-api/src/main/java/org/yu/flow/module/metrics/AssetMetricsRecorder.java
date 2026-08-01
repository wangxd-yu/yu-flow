package org.yu.flow.module.metrics;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.config.YuFlowProperties;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 资产运行计量写入（Redis 热层，多节点共享；fail-open）。
 */
@Slf4j
@Component
public class AssetMetricsRecorder {

    @Resource
    private YuFlowProperties yuFlowProperties;

    public void record(MetricsAssetType assetType, String assetId, MetricsOutcome outcome, long costMs) {
        record(assetType, assetId, outcome, costMs, MetricsKeys.TRIGGER_DEFAULT);
    }

    public void record(MetricsAssetType assetType, String assetId, MetricsOutcome outcome,
                       long costMs, String triggerType) {
        try {
            YuFlowProperties.Metrics cfg = metricsCfg();
            if (cfg == null || !cfg.isEnabled()) {
                return;
            }
            if (assetType == null || StrUtil.isBlank(assetId) || outcome == null) {
                return;
            }
            if (assetType == MetricsAssetType.SERVICE
                    && "DEBUG".equalsIgnoreCase(triggerType)
                    && !cfg.isIncludeDebug()) {
                return;
            }

            String trigger = StrUtil.isBlank(triggerType) ? MetricsKeys.TRIGGER_DEFAULT : triggerType.trim().toUpperCase();
            LocalDateTime bucket = MetricsKeys.nowMinute();
            String key = MetricsKeys.minuteBucketKey(assetType, assetId, trigger, bucket);

            switch (outcome) {
                case SUCCESS -> FlowRedisUtil.hincrBy(key, MetricsKeys.FIELD_SUCCESS, 1);
                case FAIL -> FlowRedisUtil.hincrBy(key, MetricsKeys.FIELD_FAIL, 1);
                case SKIPPED -> FlowRedisUtil.hincrBy(key, MetricsKeys.FIELD_SKIPPED, 1);
                case AUTH_FAIL -> FlowRedisUtil.hincrBy(key, MetricsKeys.FIELD_AUTH_FAIL, 1);
            }

            // 鉴权失败不计延迟直方图，避免污染 P95
            if (outcome != MetricsOutcome.SKIPPED && outcome != MetricsOutcome.AUTH_FAIL && costMs >= 0) {
                FlowRedisUtil.hincrBy(key, MetricsKeys.FIELD_SUM_COST, costMs);
                FlowRedisUtil.hincrBy(key, MetricsKeys.FIELD_LATENCY_COUNT, 1);
                int idx = LatencyHistogram.indexOf(costMs);
                FlowRedisUtil.hincrBy(key, MetricsKeys.histField(idx), 1);
            }

            FlowRedisUtil.sadd(MetricsKeys.ACTIVE_SET, key);
            int ttlHours = Math.max(1, cfg.getRedisTtlHours());
            FlowRedisUtil.expire(key, ttlHours, TimeUnit.HOURS);
            FlowRedisUtil.expire(MetricsKeys.ACTIVE_SET, ttlHours + 1L, TimeUnit.HOURS);

            updateMeta(assetType, assetId, outcome, ttlHours);
        } catch (Exception e) {
            log.warn("[AssetMetricsRecorder] 计量写入失败（已忽略）: type={}, id={}, err={}",
                    assetType, assetId, e.getMessage());
        }
    }

    private void updateMeta(MetricsAssetType type, String assetId, MetricsOutcome outcome, int ttlHours) {
        String meta = MetricsKeys.metaKey(type, assetId);
        long now = System.currentTimeMillis();
        if (outcome == MetricsOutcome.SUCCESS) {
            FlowRedisUtil.hset(meta, MetricsKeys.META_LAST_SUCCESS, String.valueOf(now));
            // 删除字段即视为 0，同时保证下次 HINCRBY 从原生整数起步
            FlowRedisUtil.hdel(meta, MetricsKeys.META_CONSEC_FAIL);
        } else if (outcome == MetricsOutcome.FAIL) {
            // 仅业务失败拉高连续失败；AUTH_FAIL 不污染健康度
            FlowRedisUtil.hset(meta, MetricsKeys.META_LAST_FAIL, String.valueOf(now));
            try {
                // 原子累加，避免并发失败时 hget/hset 互相覆盖少计
                FlowRedisUtil.hincrBy(meta, MetricsKeys.META_CONSEC_FAIL, 1);
            } catch (Exception e) {
                // 旧版本以 JSON 字符串（带引号）写入，HINCRBY 会失败；迁移为原生整数后重放
                long prev = parseLong(FlowRedisUtil.hget(meta, MetricsKeys.META_CONSEC_FAIL));
                FlowRedisUtil.hdel(meta, MetricsKeys.META_CONSEC_FAIL);
                FlowRedisUtil.hincrBy(meta, MetricsKeys.META_CONSEC_FAIL, prev + 1);
            }
        } else {
            return;
        }
        FlowRedisUtil.expire(meta, Math.max(ttlHours, 24), TimeUnit.HOURS);
        FlowRedisUtil.sadd(MetricsKeys.META_DIRTY_SET, meta);
        FlowRedisUtil.expire(MetricsKeys.META_DIRTY_SET, Math.max(ttlHours, 24) + 1L, TimeUnit.HOURS);
    }

    private static long parseLong(Object v) {
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v).replace("\"", ""));
        } catch (Exception e) {
            return 0L;
        }
    }

    private YuFlowProperties.Metrics metricsCfg() {
        return yuFlowProperties == null ? null : yuFlowProperties.getMetrics();
    }
}
