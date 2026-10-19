package org.yu.flow.config.properties;

/**
 * 消息队列（MQ 触发 / 发送节点）配置。
 *
 * <p>对应 YAML 路径：{@code yu.flow.mq.*}</p>
 * <pre>
 * yu:
 *   flow:
 *     mq:
 *       consumer-enabled: true
 *       dedup-ttl-seconds: 300
 *       send-timeout-ms: 10000
 *       max-message-bytes: 1048576
 * </pre>
 */
public class MqProperties {

    /**
     * 是否启动 MQ 消费（MqConsumerManager 总开关）。
     * <p>多实例部署时仅一个实例开启即可避免重复消费竞争；
     * 消息级幂等由 Redis 锁按 messageId 兜底。</p>
     */
    private boolean consumerEnabled = true;

    /** 消费幂等锁 TTL（秒），按 messageId 去重 */
    private int dedupTtlSeconds = 300;

    /** 发送超时（毫秒） */
    private long sendTimeoutMs = 10000;

    /** 消息体大小上限（字节），≤0 不限制。默认 1MB */
    private int maxMessageBytes = 1048576;

    /** 日志留存的原始报文最大字符数，超出截断；<=0 不记录报文 */
    private int logBodyMaxChars = 8192;

    /** 原始报文落库全局默认：FULL / MASK / OFF */
    private String logPayloadMode = "FULL";

    /** 积压查询缓存秒数（Kafka lag / Rabbit 队列深度） */
    private int backlogCacheSeconds = 60;

    public boolean isConsumerEnabled() {
        return consumerEnabled;
    }

    public void setConsumerEnabled(boolean consumerEnabled) {
        this.consumerEnabled = consumerEnabled;
    }

    public int getDedupTtlSeconds() {
        return dedupTtlSeconds;
    }

    public void setDedupTtlSeconds(int dedupTtlSeconds) {
        this.dedupTtlSeconds = dedupTtlSeconds;
    }

    public long getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public int getMaxMessageBytes() {
        return maxMessageBytes;
    }

    public void setMaxMessageBytes(int maxMessageBytes) {
        this.maxMessageBytes = maxMessageBytes;
    }

    public int getLogBodyMaxChars() {
        return logBodyMaxChars;
    }

    public void setLogBodyMaxChars(int logBodyMaxChars) {
        this.logBodyMaxChars = logBodyMaxChars;
    }

    public String getLogPayloadMode() {
        return logPayloadMode;
    }

    public void setLogPayloadMode(String logPayloadMode) {
        this.logPayloadMode = logPayloadMode;
    }

    public int getBacklogCacheSeconds() {
        return backlogCacheSeconds;
    }

    public void setBacklogCacheSeconds(int backlogCacheSeconds) {
        this.backlogCacheSeconds = backlogCacheSeconds;
    }
}
