package org.yu.flow.module.directory.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * 接收目录缓存集群刷新通知，只使本地快照失效；下次访问再按需重建。
 */
@Slf4j
@Component
public class FlowDirectoryMessageListener implements MessageListener {

    @Resource
    private FlowDirectoryServiceImpl flowDirectoryService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        flowDirectoryService.refreshDirectoryChainCache();
        log.debug("[FlowDirectory] 收到目录缓存刷新消息, channel={}",
                new String(message.getChannel(), StandardCharsets.UTF_8));
    }
}
