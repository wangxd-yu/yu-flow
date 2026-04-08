package org.yu.flow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.yu.flow.module.sysmacro.cache.SysMacroMessageListener;

import javax.annotation.Resource;

/**
 * 动态 API 路由缓存 Redis Pub/Sub 消息监听器
 *
 * <p>当任意集群节点通过 {@link FlowApiCacheManager#publishRefreshEvent()} 发布刷新消息后，
 * 本监听器会被 {@link org.springframework.data.redis.listener.RedisMessageListenerContainer} 回调，
 * 触发当前节点的本地路由缓存全量重载。</p>
 *
 * <h3>消息频道</h3>
 * <p>{@code flow:api:cache:refresh:topic}（定义在 {@link FlowApiCacheManager#REFRESH_TOPIC}）</p>
 *
 * <h3>幂等性</h3>
 * <p>{@link FlowApiCacheManager#refreshCache()} 本身是幂等的全量替换操作，
 * 即使短时间内收到多条 REFRESH 消息，多次执行也不会造成数据不一致，
 * 最多只是多次查库（可接受的代价换来架构简洁性）。</p>
 *
 * yu-flow
 * @see FlowApiCacheManager
 * @see SysMacroMessageListener
 */
@Slf4j
@Component
public class FlowApiMessageListener implements MessageListener {

    @Resource
    private FlowApiCacheManager flowApiCacheManager;

    /**
     * 接收 Redis 频道消息回调。
     *
     * @param message 消息体（内容为 "REFRESH" 字符串，暂不做细粒度解析）
     * @param pattern 匹配的频道模式（本场景固定为 null，因为使用精确 topic 订阅）
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        String channel = new String(message.getChannel());

        log.info("[FlowApiMessageListener] 收到缓存刷新消息。channel={}, body={}", channel, body);

        try {
            flowApiCacheManager.refreshCache();
            log.info("[FlowApiMessageListener] 本地路由缓存重载完成。精确路由={}, 模式路由={}",
                    flowApiCacheManager.getExactCacheSize(),
                    flowApiCacheManager.getPatternCacheSize());
        } catch (Exception e) {
            log.error("[FlowApiMessageListener] 处理缓存刷新消息时异常。", e);
        }
    }
}
