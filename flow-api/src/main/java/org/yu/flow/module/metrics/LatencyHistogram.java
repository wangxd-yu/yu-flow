package org.yu.flow.module.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;

/**
 * 固定边界耗时直方图（毫秒），用于近似 P50/P95/P99。
 */
public final class LatencyHistogram {

    /** 上界（含）；最后一桶为 Long.MAX_VALUE */
    public static final long[] BOUNDS = {
            5L, 10L, 25L, 50L, 100L, 250L, 500L, 1000L, 2500L, 5000L, 10000L, 30000L, Long.MAX_VALUE
    };

    public static final int BUCKET_COUNT = BOUNDS.length;

    private LatencyHistogram() {
    }

    public static int indexOf(long costMs) {
        long v = Math.max(0L, costMs);
        for (int i = 0; i < BOUNDS.length; i++) {
            if (v <= BOUNDS[i]) {
                return i;
            }
        }
        return BUCKET_COUNT - 1;
    }

    public static long[] empty() {
        return new long[BUCKET_COUNT];
    }

    public static void add(long[] hist, long costMs, long delta) {
        if (hist == null || hist.length != BUCKET_COUNT || delta == 0) {
            return;
        }
        hist[indexOf(costMs)] += delta;
    }

    public static void merge(long[] into, long[] from) {
        if (into == null || from == null) {
            return;
        }
        int n = Math.min(into.length, from.length);
        for (int i = 0; i < n; i++) {
            into[i] += from[i];
        }
    }

    public static String toJson(long[] hist, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(hist == null ? empty() : hist);
        } catch (Exception e) {
            return Arrays.toString(hist == null ? empty() : hist).replace(' ', ',');
        }
    }

    public static long[] fromJson(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            long[] arr = mapper.readValue(json, new TypeReference<long[]>() {
            });
            if (arr == null) {
                return empty();
            }
            if (arr.length == BUCKET_COUNT) {
                return arr;
            }
            long[] out = empty();
            System.arraycopy(arr, 0, out, 0, Math.min(arr.length, BUCKET_COUNT));
            return out;
        } catch (Exception e) {
            return empty();
        }
    }

    /**
     * 近似分位数：落到累计达到 ceil(q * n) 的桶上界（末桶用 30000 展示）。
     */
    public static Long percentile(long[] hist, double q) {
        if (hist == null) {
            return null;
        }
        long total = 0;
        for (long c : hist) {
            total += c;
        }
        if (total <= 0) {
            return null;
        }
        long target = (long) Math.ceil(q * total);
        if (target < 1) {
            target = 1;
        }
        long acc = 0;
        for (int i = 0; i < hist.length; i++) {
            acc += hist[i];
            if (acc >= target) {
                long bound = BOUNDS[i];
                return bound == Long.MAX_VALUE ? 30000L : bound;
            }
        }
        return 30000L;
    }
}
