package org.yu.flow.cache;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 智能缓存管理器条件判断
 * - 当Spring容器中没有任何CacheManager时，创建本地缓存
 * - 当容器中已有CacheManager时，使用现有的（避免冲突）
 * - 支持通过spring.yu.flow.cache.enabled=false禁用组件库缓存
 */
public class NoCacheManagerCondition implements Condition {

    private static final String CACHE_ENABLED_PROPERTY = "spring.yu.flow.cache.enabled";
    private static final String CACHE_ENABLED_DEFAULT = "true";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();

        // 1. 检查是否显式禁用组件库缓存
        String cacheEnabled = env.getProperty(CACHE_ENABLED_PROPERTY, CACHE_ENABLED_DEFAULT);
        if ("false".equalsIgnoreCase(cacheEnabled)) {
            return false; // 显式禁用，不创建缓存管理器
        }

        // 2. 检查容器中是否已有CacheManager
        String[] cacheManagerBeanNames = context.getBeanFactory().getBeanNamesForType(CacheManager.class, false, false);

        // 如果已有CacheManager，让父项目或使用方提供缓存
        if (cacheManagerBeanNames.length > 0) {
            return false;
        }

        // 3. 特殊情况：检查是否有专门的组件库缓存禁用标志
        String componentCacheDisabled = env.getProperty("spring.yu.flow.component-cache.disabled", "false");
        if ("true".equalsIgnoreCase(componentCacheDisabled)) {
            return false;
        }

        // 默认情况：没有CacheManager，创建本地缓存
        return true;
    }
}
