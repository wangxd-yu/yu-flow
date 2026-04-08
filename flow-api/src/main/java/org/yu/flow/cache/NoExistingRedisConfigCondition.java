package org.yu.flow.cache;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.cache.RedisCacheManager;

/**
 * 条件判断：仅当容器中没有 RedisTemplate 和 RedisCacheManager 时，才生效
 */
public class NoExistingRedisConfigCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 检查容器中是否已有 RedisTemplate 类型的 Bean
        boolean hasRedisTemplate = context.getBeanFactory().containsBeanDefinition(
                context.getBeanFactory().getBeanNamesForType(RedisTemplate.class, false, false).length > 0 ?
                        context.getBeanFactory().getBeanNamesForType(RedisTemplate.class)[0] : ""
        );

        // 检查容器中是否已有 RedisCacheManager 类型的 Bean
        boolean hasRedisCacheManager = context.getBeanFactory().containsBeanDefinition(
                context.getBeanFactory().getBeanNamesForType(RedisCacheManager.class, false, false).length > 0 ?
                        context.getBeanFactory().getBeanNamesForType(RedisCacheManager.class)[0] : ""
        );

        // 当两者都不存在时，返回 true（当前配置类生效）；否则不生效
        return !hasRedisTemplate && !hasRedisCacheManager;
    }
}
