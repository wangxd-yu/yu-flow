package org.yu.flow.cache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.util.Arrays;

// 条件：当 Spring 容器中没有 CacheManager 类型的 Bean 时，才生效
@Conditional(NoCacheManagerCondition.class)
//@Configuration
//@EnableCaching  // 开启缓存注解支持
public class ComponentCacheAutoConfig {

    // 本地调试用的内存缓存管理器（仅组件库单独运行时生效）
    @Bean
    public CacheManager componentCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        // 配置组件库需要的所有缓存区域，确保与 @CacheConfig 注解匹配
        cacheManager.setCaches(Arrays.asList(
                new ConcurrentMapCache("flow::api"),      // 对应 FlowApiService
                new ConcurrentMapCache("flowService"),  // 对应 FlowService 相关缓存
                new ConcurrentMapCache("componentCache") // 通用组件缓存
        ));
        return cacheManager;
    }
}
