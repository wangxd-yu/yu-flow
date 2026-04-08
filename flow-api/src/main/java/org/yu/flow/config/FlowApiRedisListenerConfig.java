package org.yu.flow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.yu.flow.module.sysmacro.cache.SysMacroRedisListenerConfig;

/**
 * 动态 API 路由缓存 Redis 消息监听容器配置
 *
 * <p>将 {@link FlowApiMessageListener} 绑定到 Redis Pub/Sub 频道
 * {@value FlowApiCacheManager#REFRESH_TOPIC}，实现集群级路由缓存刷新广播订阅。</p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>{@link RedisMessageListenerContainer} 在内部维护一个独立的线程池，
 *       用于监听 Redis 订阅频道的消息推送。</li>
 *   <li>当频道有消息到达时，容器自动回调已注册的 {@link FlowApiMessageListener#onMessage}。</li>
 *   <li>该配置 Bean 在 Spring 容器启动时自动注册，无需额外的手动启动操作。</li>
 * </ol>
 *
 * yu-flow
 * @see FlowApiCacheManager
 * @see SysMacroRedisListenerConfig
 */
@Configuration
public class FlowApiRedisListenerConfig {

    /**
     * 配置 Redis 消息监听容器，将路由缓存刷新监听器绑定到指定频道。
     *
     * @param connectionFactory        Redis 连接工厂（Spring Boot 自动配置注入）
     * @param flowApiMessageListener   路由缓存刷新消息监听器
     * @return 配置好的 Redis 消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer flowApiListenerContainer(
            RedisConnectionFactory connectionFactory,
            FlowApiMessageListener flowApiMessageListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 将监听器绑定到 API 路由缓存刷新频道
        container.addMessageListener(
                new MessageListenerAdapter(flowApiMessageListener, "onMessage"),
                new ChannelTopic(FlowApiCacheManager.REFRESH_TOPIC)
        );

        return container;
    }
}
