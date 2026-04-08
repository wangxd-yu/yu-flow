package org.yu.flow.module.sysmacro.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * 系统宏定义 Redis 消息监听容器配置
 *
 * <p>将 {@link SysMacroMessageListener} 绑定到 Redis Pub/Sub 频道
 * {@value SysMacroCacheManager#REFRESH_TOPIC}，实现集群级缓存刷新广播订阅。</p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>{@link RedisMessageListenerContainer} 在内部维护一个独立的线程池，
 *       用于监听 Redis 订阅频道的消息推送。</li>
 *   <li>当频道有消息到达时，容器自动回调已注册的 {@link SysMacroMessageListener#onMessage}。</li>
 *   <li>该配置 Bean 在 Spring 容器启动时自动注册，无需额外的手动启动操作。</li>
 * </ol>
 *
 * yu-flow
 */
@Configuration
public class SysMacroRedisListenerConfig {

    /**
     * 配置 Redis 消息监听容器，将宏缓存刷新监听器绑定到指定频道。
     *
     * @param connectionFactory Redis 连接工厂（Spring Boot 自动配置注入）
     * @param sysMacroMessageListener 宏缓存刷新消息监听器
     * @return 配置好的 Redis 消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer sysMacroListenerContainer(
            RedisConnectionFactory connectionFactory,
            SysMacroMessageListener sysMacroMessageListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 将监听器绑定到宏缓存刷新频道
        container.addMessageListener(
                new MessageListenerAdapter(sysMacroMessageListener, "onMessage"),
                new ChannelTopic(SysMacroCacheManager.REFRESH_TOPIC)
        );

        return container;
    }
}
