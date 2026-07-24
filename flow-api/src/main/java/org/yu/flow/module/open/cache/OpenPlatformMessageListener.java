package org.yu.flow.module.open.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class OpenPlatformMessageListener implements MessageListener {

    @Resource
    private OpenPlatformCache openPlatformCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            openPlatformCache.onRemoteRefresh();
        } catch (Exception e) {
            log.warn("[OpenPlatformMessageListener] 处理失败: {}", e.getMessage());
        }
    }
}
