package org.yu.flow.config.properties;

/**
 * 定时任务相关配置。
 *
 * <p>对应 YAML 路径：{@code yu.flow.task.*}</p>
 * <pre>
 * yu:
 *   flow:
 *     task:
 *       lock-ttl-minutes: 30
 *       lock-renew-interval-seconds: 60
 *       scheduler-pool-size: 4
 * </pre>
 */
public class TaskProperties {

    /**
     * 分布式执行锁 TTL（分钟）。
     * <p>Cron 触发时通过 Redis 抢锁，防止多节点重复执行；超时后锁自动释放。</p>
     */
    private int lockTtlMinutes = 30;

    /**
     * 锁心跳续期间隔（秒）。≤0 关闭续期。
     * <p>建议为 TTL 的 1/2～1/3，长任务执行期间可防止 TTL 提前丢失导致双跑。</p>
     */
    private int lockRenewIntervalSeconds = 60;

    /** Cron / 手动触发共用的 TaskScheduler 池大小 */
    private int schedulerPoolSize = 4;

    /** flowAsyncExecutor 核心线程数 */
    private int asyncCorePoolSize = 4;

    /** flowAsyncExecutor 最大线程数 */
    private int asyncMaxPoolSize = 8;

    /** flowAsyncExecutor 队列容量 */
    private int asyncQueueCapacity = 200;

    public int getLockTtlMinutes() {
        return lockTtlMinutes;
    }

    public void setLockTtlMinutes(int lockTtlMinutes) {
        this.lockTtlMinutes = lockTtlMinutes;
    }

    public int getLockRenewIntervalSeconds() {
        return lockRenewIntervalSeconds;
    }

    public void setLockRenewIntervalSeconds(int lockRenewIntervalSeconds) {
        this.lockRenewIntervalSeconds = lockRenewIntervalSeconds;
    }

    public int getSchedulerPoolSize() {
        return schedulerPoolSize;
    }

    public void setSchedulerPoolSize(int schedulerPoolSize) {
        this.schedulerPoolSize = schedulerPoolSize;
    }

    public int getAsyncCorePoolSize() {
        return asyncCorePoolSize;
    }

    public void setAsyncCorePoolSize(int asyncCorePoolSize) {
        this.asyncCorePoolSize = asyncCorePoolSize;
    }

    public int getAsyncMaxPoolSize() {
        return asyncMaxPoolSize;
    }

    public void setAsyncMaxPoolSize(int asyncMaxPoolSize) {
        this.asyncMaxPoolSize = asyncMaxPoolSize;
    }

    public int getAsyncQueueCapacity() {
        return asyncQueueCapacity;
    }

    public void setAsyncQueueCapacity(int asyncQueueCapacity) {
        this.asyncQueueCapacity = asyncQueueCapacity;
    }
}
