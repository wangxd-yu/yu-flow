package org.yu.flow.module.mq.provider;

/**
 * MQ 订阅句柄：可关闭、可查询运行状态。
 */
public interface MqSubscription extends AutoCloseable {

    /** 订阅是否仍在运行 */
    boolean isRunning();

    /** 停止订阅并释放监听资源（幂等） */
    @Override
    void close();
}
