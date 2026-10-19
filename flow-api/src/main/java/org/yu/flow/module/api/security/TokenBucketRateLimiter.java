package org.yu.flow.module.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 令牌桶限流（Redis Lua，单次往返）。
 *
 * <p>桶状态存在 Redis 的 hash 里，时间取 Redis 服务端的 {@code TIME}，多节点共用同一个桶、不受各节点时钟偏差影响。
 * 每秒补充 {@code rate} 个令牌，最多攒 {@code capacity} 个；相比固定窗口，不会在窗口交界处放过两倍流量。</p>
 */
@Slf4j
@Component
public class TokenBucketRateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local rate = tonumber(ARGV[1]) "
                    + "local capacity = tonumber(ARGV[2]) "
                    + "local t = redis.call('TIME') "
                    + "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000) "
                    + "local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts') "
                    + "local tokens = tonumber(state[1]) "
                    + "local ts = tonumber(state[2]) "
                    + "if tokens == nil or ts == nil then tokens = capacity ts = now end "
                    + "local elapsed = now - ts "
                    + "if elapsed < 0 then elapsed = 0 end "
                    + "tokens = math.min(capacity, tokens + elapsed * rate / 1000) "
                    + "local allowed = 0 "
                    + "if tokens >= 1 then tokens = tokens - 1 allowed = 1 end "
                    + "redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now)) "
                    + "redis.call('PEXPIRE', KEYS[1], math.ceil(capacity * 1000 / rate) + 1000) "
                    + "return allowed",
            Long.class);

    private final StringRedisTemplate redis;

    public TokenBucketRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param key      完整 Redis key（调用方负责业务前缀与维度）
     * @param rate     每秒补充的令牌数；≤0 不限流
     * @param capacity 桶容量（允许的瞬时突发）；≤0 时按 rate
     * @param failOpen Redis 异常时是否放行
     * @return true=允许；false=超限或按配置在故障时拒绝
     */
    public boolean tryAcquire(String key, int rate, int capacity, boolean failOpen) {
        if (rate <= 0) {
            return true;
        }
        int cap = capacity > 0 ? capacity : rate;
        try {
            Long allowed = redis.execute(SCRIPT, Collections.singletonList(key),
                    String.valueOf(rate), String.valueOf(cap));
            return allowed != null && allowed == 1L;
        } catch (Exception e) {
            if (failOpen) {
                log.warn("[TokenBucketRateLimiter] 限流计数失败，按配置放行: {}", e.getMessage());
                return true;
            }
            log.error("[TokenBucketRateLimiter] 限流计数失败，按配置拒绝: {}", e.getMessage());
            return false;
        }
    }
}
