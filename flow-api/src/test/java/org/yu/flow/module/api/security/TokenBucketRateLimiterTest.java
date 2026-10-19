package org.yu.flow.module.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TokenBucketRateLimiterTest {

    private final StringRedisTemplate brokenRedis = mock(StringRedisTemplate.class, invocation -> {
        throw new RedisConnectionFailureException("redis down");
    });
    private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(brokenRedis);

    @Test
    void redisFailureFollowsFailMode() {
        assertTrue(limiter.tryAcquire("k", 10, 10, true));
        assertFalse(limiter.tryAcquire("k", 10, 10, false));
    }

    @Test
    void nonPositiveRateNeverTouchesRedis() {
        assertTrue(limiter.tryAcquire("k", 0, 10, false));
        assertTrue(limiter.tryAcquire("k", -1, 0, false));
    }
}
