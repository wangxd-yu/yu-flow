package org.yu.flow.module.sysconfig.cache;

import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置缓存管理器（L1 本地缓存 + L2 Redis Pub/Sub 集群广播）
 *
 * <p>网关执行需要“极高频读取、极低频修改”的系统参数，采用 ConcurrentHashMap 作为一级缓存，保证 O(1) 无锁读取性能。</p>
 */
@Slf4j
@Component
public class SysConfigCacheManager {

    public static final String REFRESH_TOPIC = "flow:sys:config:refresh:topic";

    /**
     * L1 本地缓存：configKey → SysConfigDO 快照
     */
    private volatile Map<String, SysConfigDO> CONFIG_CACHE = new ConcurrentHashMap<>(128);

    @Resource
    private SysConfigRepository sysConfigRepository;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        log.info("[SysConfigCacheManager] 应用启动，开始全量加载系统配置缓存...");
        reloadAll();
    }

    public void reloadAll() {
        try {
            List<SysConfigDO> activeConfigs = sysConfigRepository.findAllByStatus(1);

            Map<String, SysConfigDO> newCache = new ConcurrentHashMap<>(activeConfigs.size() * 2);

            for (SysConfigDO config : activeConfigs) {
                newCache.put(config.getConfigKey(), config);
            }

            this.CONFIG_CACHE = newCache;

            log.info("[SysConfigCacheManager] 缓存重载完成。加载项数={}", activeConfigs.size());
        } catch (Exception e) {
            log.error("[SysConfigCacheManager] 全量重载缓存异常，保留旧缓存继续服务。", e);
        }
    }

    /**
     * 获取原始系统配置实体
     */
    public Optional<SysConfigDO> getConfig(String configKey) {
        return Optional.ofNullable(CONFIG_CACHE.get(configKey));
    }

    /**
     * 获取字符串类型的配置值
     */
    public String getStringConfig(String configKey, String defaultValue) {
        return getConfig(configKey).map(SysConfigDO::getConfigValue).orElse(defaultValue);
    }

    /**
     * 获取数字类型的配置值
     */
    public Integer getIntConfig(String configKey, Integer defaultValue) {
        return getConfig(configKey)
                .map(SysConfigDO::getConfigValue)
                .map(v -> Convert.toInt(v, defaultValue))
                .orElse(defaultValue);
    }

    /**
     * 获取布尔类型的配置值
     */
    public Boolean getBoolConfig(String configKey, Boolean defaultValue) {
        return getConfig(configKey)
                .map(SysConfigDO::getConfigValue)
                .map(v -> Convert.toBool(v, defaultValue))
                .orElse(defaultValue);
    }

    /**
     * 获取 JSON 类型的配置值并反序列化为指定类型
     */
    public <T> T getJsonConfig(String configKey, Class<T> clazz) {
        return getConfig(configKey)
                .map(SysConfigDO::getConfigValue)
                .map(v -> {
                    try {
                        return JSONUtil.toBean(v, clazz);
                    } catch (Exception e) {
                        log.warn("配置项 {} JSON 解析失败", configKey, e);
                        return null;
                    }
                })
                .orElse(null);
    }

    public int getCacheSize() {
        return CONFIG_CACHE.size();
    }

    public void publishRefreshEvent() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublishRefreshEvent();
                }
            });
        } else {
            doPublishRefreshEvent();
        }
    }

    private void doPublishRefreshEvent() {
        try {
            stringRedisTemplate.convertAndSend(REFRESH_TOPIC, "REFRESH");
            log.info("[SysConfigCacheManager] 已发布缓存刷新事件到 Redis 频道: {}", REFRESH_TOPIC);
        } catch (Exception e) {
            log.error("[SysConfigCacheManager] 发布缓存刷新事件失败，尝试本地降级刷新。", e);
            reloadAll();
        }
    }
}
