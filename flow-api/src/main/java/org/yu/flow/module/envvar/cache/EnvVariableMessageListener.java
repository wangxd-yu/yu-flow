package org.yu.flow.module.envvar.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class EnvVariableMessageListener implements MessageListener {

    @Resource
    private EnvVariableCacheManager envVariableCacheManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            envVariableCacheManager.reloadAll();
        } catch (Exception e) {
            log.warn("[EnvVariableMessageListener] 处理失败: {}", e.getMessage());
        }
    }
}
