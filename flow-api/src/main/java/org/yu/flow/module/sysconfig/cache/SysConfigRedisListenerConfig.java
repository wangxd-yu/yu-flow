package org.yu.flow.module.sysconfig.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * 系统配置 Redis 消息监听容器配置
 */
@Configuration
public class SysConfigRedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer sysConfigListenerContainer(
            RedisConnectionFactory connectionFactory,
            SysConfigMessageListener sysConfigMessageListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        container.addMessageListener(
                new MessageListenerAdapter(sysConfigMessageListener, "onMessage"),
                new ChannelTopic(SysConfigCacheManager.REFRESH_TOPIC)
        );

        return container;
    }
}
