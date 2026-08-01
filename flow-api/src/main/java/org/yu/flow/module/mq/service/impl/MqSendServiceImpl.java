package org.yu.flow.module.mq.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.mq.provider.MqConnectionSpec;
import org.yu.flow.module.mq.provider.MqProviderRegistry;
import org.yu.flow.module.mq.provider.MqSendResult;
import org.yu.flow.module.mq.service.MqConnectionService;
import org.yu.flow.module.mq.service.MqSendService;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * MQ 消息发送服务实现：连接规格解析 → 消息大小校验 → Provider SPI 发送。
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class MqSendServiceImpl implements MqSendService {

    @Resource
    private MqConnectionService mqConnectionService;

    @Resource
    private MqProviderRegistry mqProviderRegistry;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Override
    public MqSendResult send(String connectionCode, String topic, String messageKey,
                             Map<String, Object> headers, String message) {
        demoModeGuard.checkMqSend(topic);

        YuFlowProperties.Mq mqProps = yuFlowProperties.getMq();
        int maxBytes = mqProps.getMaxMessageBytes();
        if (maxBytes > 0 && message != null
                && message.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new FlowException("MQ_MESSAGE_TOO_LARGE",
                    "消息体超出大小限制（" + maxBytes + " 字节）");
        }

        MqConnectionSpec spec = mqConnectionService.buildSpec(connectionCode);
        MqSendResult result = mqProviderRegistry.getProvider(spec.getMqType())
                .send(spec, topic, messageKey, headers, message, mqProps.getSendTimeoutMs());
        log.debug("[MQ] 消息已发送 connection={}, topic={}, messageId={}",
                connectionCode, topic, result != null ? result.getMessageId() : null);
        return result;
    }
}
