package org.yu.flow.module.mq.provider;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.exception.FlowException;

import java.util.HashMap;
import java.util.Map;

/**
 * MQ Provider 注册表：按 mqType（RABBITMQ / KAFKA）路由到对应 Provider。
 *
 * <p>Provider 内部按 connectionCode 缓存客户端；连接配置变更/删除时
 * 调用 {@link #invalidate(String)} 广播销毁。</p>
 */
@Slf4j
@Component
public class MqProviderRegistry {

    private final Map<String, MqProvider> providers = new HashMap<>();

    public MqProviderRegistry() {
        register(new RabbitMqProvider());
        register(new KafkaMqProvider());
    }

    private void register(MqProvider provider) {
        providers.put(provider.getType(), provider);
    }

    /**
     * 按 MQ 类型获取 Provider，类型不支持时抛 FlowException。
     */
    public MqProvider getProvider(String mqType) {
        if (StrUtil.isBlank(mqType)) {
            throw new FlowException("MQ_TYPE_REQUIRED", "MQ 类型不能为空");
        }
        MqProvider provider = providers.get(mqType.trim().toUpperCase());
        if (provider == null) {
            throw new FlowException("MQ_TYPE_UNSUPPORTED",
                    "不支持的 MQ 类型: " + mqType + "（当前支持 " + String.join("/", providers.keySet()) + "）");
        }
        return provider;
    }

    /** 连接配置变更/删除时，销毁全部 Provider 中该编码的缓存客户端 */
    public void invalidate(String connectionCode) {
        if (StrUtil.isBlank(connectionCode)) {
            return;
        }
        for (MqProvider provider : providers.values()) {
            try {
                provider.invalidate(connectionCode);
            } catch (Exception e) {
                log.warn("[MQ] 销毁缓存客户端失败 type={}, code={}: {}",
                        provider.getType(), connectionCode, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        // 应用关闭时销毁全部缓存客户端（Provider 各自持有的工厂）
        for (MqProvider provider : providers.values()) {
            if (provider instanceof RabbitMqProvider || provider instanceof KafkaMqProvider) {
                // 逐连接销毁由 Provider 内部缓存 key 管理，这里无法枚举，交由 JVM 退出释放
                log.info("[MQ] Provider {} 随应用关闭释放", provider.getType());
            }
        }
    }
}
