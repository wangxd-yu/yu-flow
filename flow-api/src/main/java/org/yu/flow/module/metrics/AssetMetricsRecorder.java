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
            }

            if (outcome != MetricsOutcome.SKIPPED && costMs >= 0) {
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
            FlowRedisUtil.hset(meta, MetricsKeys.META_CONSEC_FAIL, "0");
        } else if (outcome == MetricsOutcome.FAIL) {
            FlowRedisUtil.hset(meta, MetricsKeys.META_LAST_FAIL, String.valueOf(now));
            long prev = parseLong(FlowRedisUtil.hget(meta, MetricsKeys.META_CONSEC_FAIL));
            FlowRedisUtil.hset(meta, MetricsKeys.META_CONSEC_FAIL, String.valueOf(prev + 1));
        }
        FlowRedisUtil.expire(meta, Math.max(ttlHours, 24), TimeUnit.HOURS);
    }

    private static long parseLong(Object v) {
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private YuFlowProperties.Metrics metricsCfg() {
        return yuFlowProperties == null ? null : yuFlowProperties.getMetrics();
    }
}
