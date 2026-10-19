package org.yu.flow.config.properties;

/**
 * 资产运行计量配置。
 *
 * <p>对应 YAML 路径：{@code yu.flow.metrics.*}</p>
 */
public class MetricsProperties {

    /** 总开关；关闭后 Recorder 直接 no-op */
    private boolean enabled = true;

    /** 是否将服务 DEBUG 触发计入生产计量 */
    private boolean includeDebug = false;

    /** Redis 分钟桶 TTL（小时） */
    private int redisTtlHours = 3;

    /** MySQL 分钟汇总保留天数；≤0 表示不清理 */
    private int retainDays = 90;

    /** Flush 间隔（秒） */
    private int flushIntervalSeconds = 30;

    /** Flush 分布式锁 TTL（秒） */
    private int flushLockTtlSeconds = 55;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIncludeDebug() {
        return includeDebug;
    }

    public void setIncludeDebug(boolean includeDebug) {
        this.includeDebug = includeDebug;
    }

    public int getRedisTtlHours() {
        return redisTtlHours;
    }

    public void setRedisTtlHours(int redisTtlHours) {
        this.redisTtlHours = redisTtlHours;
    }

    public int getRetainDays() {
        return retainDays;
    }

    public void setRetainDays(int retainDays) {
        this.retainDays = retainDays;
    }

    public int getFlushIntervalSeconds() {
        return flushIntervalSeconds;
    }

    public void setFlushIntervalSeconds(int flushIntervalSeconds) {
        this.flushIntervalSeconds = flushIntervalSeconds;
    }

    public int getFlushLockTtlSeconds() {
        return flushLockTtlSeconds;
    }

    public void setFlushLockTtlSeconds(int flushLockTtlSeconds) {
        this.flushLockTtlSeconds = flushLockTtlSeconds;
    }
}
