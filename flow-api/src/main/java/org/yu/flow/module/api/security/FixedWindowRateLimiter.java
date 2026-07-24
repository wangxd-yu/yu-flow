package org.yu.flow.module.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.cache.FlowRedisUtil;

import java.util.concurrent.TimeUnit;

/**
 * 秒级固定窗口限流（Redis INCR）。
 */
@Slf4j
@Component
public class FixedWindowRateLimiter {

    /**
     * @param redisKey   完整 Redis key（调用方负责拼业务前缀与窗口）
     * @param qps        窗口内允许次数
     * @param failOpen   Redis 异常时是否放行
     * @return true=允许；false=超限
     */
    public boolean tryAcquire(String redisKey, int qps, boolean failOpen) {
        if (qps <= 0) {
            return true;
        }
        try {
            long n = FlowRedisUtil.incr(redisKey, 1);
            if (n == 1L) {
                FlowRedisUtil.expire(redisKey, 2, TimeUnit.SECONDS);
            }
            return n <= qps;
        } catch (Exception e) {
            if (failOpen) {
                log.warn("[FixedWindowRateLimiter] 限流计数失败（fail-open）: {}", e.getMessage());
                return true;
            }
            log.error("[FixedWindowRateLimiter] 限流计数失败（fail-closed）: {}", e.getMessage());
            return false;
        }
    }

    /** 当前秒窗口 key 后缀 */
    public static long currentWindowSec() {
        return System.currentTimeMillis() / 1000L;
    }
}
