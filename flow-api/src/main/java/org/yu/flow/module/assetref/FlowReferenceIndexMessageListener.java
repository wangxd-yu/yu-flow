package org.yu.flow.module.assetref;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class FlowReferenceIndexMessageListener implements MessageListener {

    @Resource
    private FlowReferenceIndex flowReferenceIndex;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        log.info("[FlowReferenceIndexListener] 收到消息 channel={}", channel);
        try {
            flowReferenceIndex.onRemoteRefresh();
        } catch (Exception e) {
            log.error("[FlowReferenceIndexListener] 重建失败: {}", e.getMessage(), e);
        }
    }
}
