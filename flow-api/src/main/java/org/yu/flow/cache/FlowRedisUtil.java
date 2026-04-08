package org.yu.flow.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class FlowRedisUtil {

    // 引入 Jackson 的 ObjectMapper（确保已配置 JavaTimeModule 支持时间类型）
    @Resource
    private ObjectMapper flowObjectMapper;

    private static FlowRedisUtil redisUtil;

    @PostConstruct
    private void init() {
        redisUtil = this;
    }

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // ============================ String 类型 ============================

    /**
     * 设置值
     */
    public static boolean set(String key, Object value) {
        try {
            redisUtil.redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.error("[FlowRedisUtil] set 失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 设置值并指定过期时间
     */
    public static boolean set(String key, Object value, long time, TimeUnit unit) {
        try {
            redisUtil.redisTemplate.opsForValue().set(key, value, time, unit);
            return true;
        } catch (Exception e) {
            log.error("[FlowRedisUtil] set with TTL 失败: key={}, ttl={} {}", key, time, unit, e);
            return false;
        }
    }

    /**
     * 获取值
     */
    public static Object get(String key) {
        return key == null ? null : redisUtil.redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除 key
     */
    public static boolean delete(String key) {
        return redisUtil.redisTemplate.delete(key);
    }

    /**
     * 批量删除 key
     */
    public static long delete(Collection<String> keys) {
        return redisUtil.redisTemplate.delete(keys);
    }

    /**
     * 设置过期时间
     */
    public static boolean expire(String key, long time, TimeUnit unit) {
        return redisUtil.redisTemplate.expire(key, time, unit);
    }

    /**
     * 获取过期时间（-1：永久有效，-2：key 不存在）
     */
    public static long getExpire(String key, TimeUnit unit) {
        return redisUtil.redisTemplate.getExpire(key, unit);
    }

    /**
     * 判断 key 是否存在
     */
    public static boolean hasKey(String key) {
        return redisUtil.redisTemplate.hasKey(key);
    }

    /**
     * 自增（+1）
     */
    public static long incr(String key, long delta) {
        return redisUtil.redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 自减（-1）
     */
    public static long decr(String key, long delta) {
        return redisUtil.redisTemplate.opsForValue().decrement(key, delta);
    }

    // ============================ Hash 类型 ============================

    /**
     * Hash 设置字段值
     */
    public static boolean hset(String key, String hashKey, Object value) {
        try {
            redisUtil.redisTemplate.opsForHash().put(key, hashKey, value);
            return true;
        } catch (Exception e) {
            log.error("[FlowRedisUtil] hset 失败: key={}, hashKey={}", key, hashKey, e);
            return false;
        }
    }

    /**
     * Hash 获取字段值
     */
    public static Object hget(String key, String hashKey) {
        return redisUtil.redisTemplate.opsForHash().get(key, hashKey);
    }

    // 新增：带类型参数的 hget 方法，支持反序列化为指定类型
    public static <T> T hget(String key, String hashKey, Class<T> clazz) {
        Object value = redisUtil.redisTemplate.opsForHash().get(key, hashKey);
        if (value == null) {
            return null;
        }
        // 如果是 LinkedHashMap，手动转换为目标类型
        if (value instanceof Map) {
            try {
                // 将 Map 转换为指定对象
                return redisUtil.flowObjectMapper.convertValue(value, clazz);
            } catch (Exception e) {
                throw new RuntimeException("Redis hash 转换为 " + clazz.getName() + " 失败", e);
            }
        }
        // 如果已经是目标类型，直接返回
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return null;
    }

    /**
     * Hash 获取所有字段和值
     */
    public static Map<Object, Object> hgetAll(String key) {
        return redisUtil.redisTemplate.opsForHash().entries(key);
    }

    /**
     * Hash 批量设置字段值
     */
    public static boolean hmset(String key, Map<String, Object> map) {
        try {
            redisUtil.redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            log.error("[FlowRedisUtil] hmset 失败: key={}", key, e);
            return false;
        }
    }

    /**
     * Hash 删除字段
     */
    public static long hdel(String key, Object... hashKeys) {
        return redisUtil.redisTemplate.opsForHash().delete(key, hashKeys);
    }

    /**
     * 判断 Hash 字段是否存在
     */
    public static boolean hhasKey(String key, String hashKey) {
        return redisUtil.redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    /**
     * Hash 获取所有字段名（key）
     */
    @SuppressWarnings("unchecked")
    public static Set<String> hkeys(String key) {
        Set<?> keys = redisUtil.redisTemplate.opsForHash().keys(key);
        Set<String> result = new java.util.HashSet<>();
        for (Object k : keys) {
            result.add(k.toString());
        }
        return result;
    }

    // ============================ List 类型 ============================

    /**
     * List 左添加元素
     */
    public static long lpush(String key, Object... values) {
        return redisUtil.redisTemplate.opsForList().leftPushAll(key, values);
    }

    /**
     * List 右添加元素
     */
    public static long rpush(String key, Object... values) {
        return redisUtil.redisTemplate.opsForList().rightPushAll(key, values);
    }

    /**
     * List 获取指定范围元素
     */
    public static List<Object> lrange(String key, long start, long end) {
        return redisUtil.redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * List 移除并返回左侧第一个元素
     */
    public static Object lpop(String key) {
        return redisUtil.redisTemplate.opsForList().leftPop(key);
    }

    /**
     * List 移除并返回右侧第一个元素
     */
    public static Object rpop(String key) {
        return redisUtil.redisTemplate.opsForList().rightPop(key);
    }

    /**
     * List 获取长度
     */
    public static long lsize(String key) {
        return redisUtil.redisTemplate.opsForList().size(key);
    }

    // ============================ Set 类型 ============================

    /**
     * Set 添加元素
     */
    public static long sadd(String key, Object... values) {
        return redisUtil.redisTemplate.opsForSet().add(key, values);
    }

    /**
     * Set 获取所有元素
     */
    public static Set<Object> smembers(String key) {
        return redisUtil.redisTemplate.opsForSet().members(key);
    }

    /**
     * Set 移除元素
     */
    public static long srem(String key, Object... values) {
        return redisUtil.redisTemplate.opsForSet().remove(key, values);
    }

    /**
     * 判断元素是否在 Set 中
     */
    public static boolean sismember(String key, Object value) {
        return redisUtil.redisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * Set 获取元素数量
     */
    public static long ssize(String key) {
        return redisUtil.redisTemplate.opsForSet().size(key);
    }

    // ============================ ZSet 类型 ============================

    /**
     * ZSet 添加元素（带分数）
     */
    public static boolean zadd(String key, Object value, double score) {
        return redisUtil.redisTemplate.opsForZSet().add(key, value, score);
    }

    /**
     * ZSet 获取指定范围元素（升序）
     */
    public static Set<Object> zrange(String key, long start, long end) {
        return redisUtil.redisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * ZSet 获取指定范围元素（降序）
     */
    public static Set<Object> zrevrange(String key, long start, long end) {
        return redisUtil.redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    /**
     * ZSet 移除元素
     */
    public static long zrem(String key, Object... values) {
        return redisUtil.redisTemplate.opsForZSet().remove(key, values);
    }

    /**
     * ZSet 获取元素数量
     */
    public static long zsize(String key) {
        return redisUtil.redisTemplate.opsForZSet().size(key);
    }

    // ============================ 键操作 ============================

    /**
     * 重命名键（原子操作）
     * 注意：若目标键已存在，会被覆盖
     * @param oldKey 原键
     * @param newKey 新键
     * @return true=重命名成功，false=失败（原键不存在或其他异常）
     */
    public static boolean rename(String oldKey, String newKey) {
        try {
            // 调用 RedisTemplate 的 rename 方法，底层执行 Redis 的 RENAME 命令
            redisUtil.redisTemplate.rename(oldKey, newKey);
            return true;
        } catch (Exception e) {
            // 捕获异常（如原键不存在会抛出 RedisSystemException）
            log.error("[FlowRedisUtil] rename 失败: oldKey={}, newKey={}", oldKey, newKey, e);
            return false;
        }
    }

    /**
     * 仅当目标键不存在时重命名（原子操作，避免覆盖已有键）
     * @param oldKey 原键
     * @param newKey 新键
     * @return true=重命名成功，false=目标键已存在或原键不存在
     */
    public static boolean renameIfAbsent(String oldKey, String newKey) {
        try {
            // 调用 RedisTemplate 的 renameIfAbsent 方法，底层执行 Redis 的 RENAME NX 命令
            return Boolean.TRUE.equals(redisUtil.redisTemplate.renameIfAbsent(oldKey, newKey));
        } catch (Exception e) {
            log.error("[FlowRedisUtil] renameIfAbsent 失败: oldKey={}, newKey={}", oldKey, newKey, e);
            return false;
        }
    }
}
