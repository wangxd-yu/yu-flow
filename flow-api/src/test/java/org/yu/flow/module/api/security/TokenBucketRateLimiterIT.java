package org.yu.flow.module.api.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lua 脚本在真实 Redis 上的行为：突发容量、按速率补充、默认容量、过期时间。
 */
@Testcontainers
class TokenBucketRateLimiterIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static TokenBucketRateLimiter limiter;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        limiter = new TokenBucketRateLimiter(redis);
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void allowsBurstUpToCapacityThenDenies() {
        String key = key();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(key, 1, 5, false), "第 " + (i + 1) + " 个请求应在突发容量内");
        }
        assertFalse(limiter.tryAcquire(key, 1, 5, false));
    }

    @Test
    void refillsAtConfiguredRate() throws Exception {
        String key = key();
        assertTrue(limiter.tryAcquire(key, 20, 1, false));
        assertFalse(limiter.tryAcquire(key, 20, 1, false));

        Thread.sleep(150);

        assertTrue(limiter.tryAcquire(key, 20, 1, false));
        assertFalse(limiter.tryAcquire(key, 20, 1, false), "容量为 1 时补充的令牌不能超过容量");
    }

    @Test
    void capacityDefaultsToRate() {
        String key = key();
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.tryAcquire(key, 3, 0, false)) {
                allowed++;
            }
        }
        assertEquals(3, allowed);
    }

    @Test
    void bucketsAreIndependentPerKey() {
        String a = key();
        String b = key();
        assertTrue(limiter.tryAcquire(a, 1, 1, false));
        assertFalse(limiter.tryAcquire(a, 1, 1, false));
        assertTrue(limiter.tryAcquire(b, 1, 1, false));
    }

    @Test
    void stateExpiresAfterBucketWouldBeFull() {
        String key = key();
        limiter.tryAcquire(key, 10, 50, false);

        Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(ttl != null && ttl > 0 && ttl <= 6000, "ttl=" + ttl);
    }

    private static String key() {
        return "flow:ingress:tb:test:" + UUID.randomUUID();
    }
}
