package org.yu.flow.module.sysconfig.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 系统配置 Redis Pub/Sub 消息监听器
 *
 * <p>当任意集群节点通过 {@link SysConfigCacheManager#publishRefreshEvent()} 发布刷新消息后，
 * 本监听器会被触发，进行当前节点的本地缓存全量重载。</p>
 */
@Slf4j
@Component
public class SysConfigMessageListener implements MessageListener {

    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        String channel = new String(message.getChannel());

        log.info("[SysConfigMessageListener] 收到缓存刷新消息。channel={}, body={}", channel, body);

        try {
            sysConfigCacheManager.reloadAll();
            log.info("[SysConfigMessageListener] 本地缓存重载完成。当前缓存大小={}",
                    sysConfigCacheManager.getCacheSize());
        } catch (Exception e) {
            log.error("[SysConfigMessageListener] 处理缓存刷新消息时异常。", e);
        }
    }
}
