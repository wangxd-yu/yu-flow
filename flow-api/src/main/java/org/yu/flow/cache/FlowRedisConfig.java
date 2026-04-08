package org.yu.flow.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching // 开启缓存支持
// 关键：仅当没有其他 Redis 配置时，当前配置才生效
@Conditional(NoExistingRedisConfigCondition.class)
public class FlowRedisConfig {

    /**
     * 配置 RedisTemplate（自定义序列化，避免默认 JDK 序列化的乱码问题）
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 1. 初始化 JSON 序列化器，并注册 Java8 时间模块
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(
                new ObjectMapper()
                        .registerModule(new JavaTimeModule()) // 关键：注册 Java8 时间模块
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // 禁用时间戳序列化（改为字符串）
        );

        // 2. Key 序列化（String 类型）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 3. Value 序列化（JSON 格式，已支持 LocalDateTime）
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置 RedisCacheManager（适配 @Cacheable 等注解）
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // 默认缓存配置（过期时间 1 小时，禁用 null 值缓存）
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // 自定义缓存配置（可针对不同缓存名称设置不同过期时间）
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("userCache", defaultConfig.entryTtl(Duration.ofHours(2))); // 用户缓存 2 小时
        cacheConfigs.put("orderCache", defaultConfig.entryTtl(Duration.ofMinutes(30))); // 订单缓存 30 分钟

        // 构建缓存管理器
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware() // 支持事务同步
                .build();
    }

    /**
     * 配置 Lettuce 连接池（Redis 客户端连接池，优化性能）
     * 若使用 Spring Boot 2.x+，默认已集成 Lettuce，无需额外依赖
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 连接池配置会自动读取 application.yml 中的 spring.redis.lettuce.pool 配置
        // 此处无需硬编码，保持默认即可，方便通过配置文件动态调整
        return new LettuceConnectionFactory();
    }
}
