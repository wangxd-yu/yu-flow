package org.yu.flow.module.metrics;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Redis / 时间桶工具。
 */
public final class MetricsKeys {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public static final String ACTIVE_SET = "flow:metrics:active";
    public static final String FLUSH_LOCK = "flow:metrics:flush:lock";
    public static final String CLEANUP_LOCK = "flow:metrics:cleanup:lock";

    public static final String FIELD_SUCCESS = "success";
    public static final String FIELD_FAIL = "fail";
    public static final String FIELD_SKIPPED = "skipped";
    public static final String FIELD_SUM_COST = "sumCostMs";
    public static final String FIELD_LATENCY_COUNT = "latencyCount";

    public static final String META_LAST_SUCCESS = "lastSuccessAt";
    public static final String META_LAST_FAIL = "lastFailAt";
    public static final String META_CONSEC_FAIL = "consecFail";

    /** API 无触发维度时的占位 */
    public static final String TRIGGER_DEFAULT = "_";

    private MetricsKeys() {
    }

    public static LocalDateTime minuteFloor(LocalDateTime t) {
        return t.truncatedTo(ChronoUnit.MINUTES);
    }

    public static LocalDateTime nowMinute() {
        return minuteFloor(LocalDateTime.now(ZONE));
    }

    public static String minuteLabel(LocalDateTime bucket) {
        return bucket.format(MINUTE_FMT);
    }

    public static LocalDateTime parseMinuteLabel(String label) {
        return LocalDateTime.parse(label, MINUTE_FMT);
    }

    public static Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZONE).toInstant());
    }

    public static LocalDateTime toLocal(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZONE);
    }

    public static String minuteBucketKey(MetricsAssetType type, String assetId, String trigger, LocalDateTime bucket) {
        String trig = (trigger == null || trigger.isBlank()) ? TRIGGER_DEFAULT : trigger.trim().toUpperCase();
        return "flow:metrics:m:" + type.name() + ":" + assetId + ":" + trig + ":" + minuteLabel(bucket);
    }

    public static String metaKey(MetricsAssetType type, String assetId) {
        return "flow:metrics:meta:" + type.name() + ":" + assetId;
    }

    public static String histField(int index) {
        return "h" + index;
    }

    /**
     * 解析桶 key：flow:metrics:m:{type}:{assetId}:{trigger}:{yyyyMMddHHmm}
     */
    public static BucketKeyParts parseBucketKey(String key) {
        if (key == null || !key.startsWith("flow:metrics:m:")) {
            return null;
        }
        String[] parts = key.split(":");
        // flow metrics m TYPE assetId trigger yyyyMMddHHmm → 7 parts when no colon in ids
        if (parts.length < 7) {
            return null;
        }
        try {
            MetricsAssetType type = MetricsAssetType.valueOf(parts[3]);
            String assetId = parts[4];
            String trigger = parts[5];
            LocalDateTime bucket = parseMinuteLabel(parts[6]);
            return new BucketKeyParts(type, assetId, trigger, bucket, key);
        } catch (Exception e) {
            return null;
        }
    }

    public record BucketKeyParts(
            MetricsAssetType assetType,
            String assetId,
            String triggerType,
            LocalDateTime bucketStart,
            String redisKey
    ) {
    }
}
