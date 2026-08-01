package org.yu.flow.module.mq.provider;

/**
 * MQ 消息监听器。
 *
 * <p>实现方（MqConsumerManager）必须自行捕获全部异常并 ack + FAIL 日志，
 * 不得向 Provider 抛出，避免毒消息 requeue 死循环。</p>
 */
@FunctionalInterface
public interface MqMessageListener {

    void onMessage(MqMessage message);
}
