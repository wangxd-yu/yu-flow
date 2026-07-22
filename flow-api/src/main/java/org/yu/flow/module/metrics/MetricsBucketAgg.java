package org.yu.flow.module.metrics;

import lombok.Data;

/**
 * 分钟桶内存聚合。
 */
@Data
public class MetricsBucketAgg {
    private long success;
    private long fail;
    private long skipped;
    private long sumCostMs;
    private long latencyCount;
    private long[] hist = LatencyHistogram.empty();

    public void add(MetricsBucketAgg other) {
        if (other == null) {
            return;
        }
        this.success += other.success;
        this.fail += other.fail;
        this.skipped += other.skipped;
        this.sumCostMs += other.sumCostMs;
        this.latencyCount += other.latencyCount;
        LatencyHistogram.merge(this.hist, other.hist);
    }

    public void addCounts(long s, long f, long sk, long sum, long lat, long[] h) {
        this.success += s;
        this.fail += f;
        this.skipped += sk;
        this.sumCostMs += sum;
        this.latencyCount += lat;
        LatencyHistogram.merge(this.hist, h);
    }

    public long terminalCalls() {
        return success + fail;
    }

    public long totalCalls() {
        return success + fail + skipped;
    }

    public Double successRate() {
        long t = terminalCalls();
        if (t <= 0) {
            return null;
        }
        return (double) success / (double) t;
    }

    public Double avgCostMs() {
        if (latencyCount <= 0) {
            return null;
        }
        return (double) sumCostMs / (double) latencyCount;
    }
}
