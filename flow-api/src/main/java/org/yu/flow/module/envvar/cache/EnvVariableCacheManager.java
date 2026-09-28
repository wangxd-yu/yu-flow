package org.yu.flow.module.envvar.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.yu.flow.module.envvar.domain.SysEnvVariableDO;
import org.yu.flow.module.envvar.repository.SysEnvVariableRepository;
import org.yu.flow.util.AesEncryptUtil;
import org.yu.flow.util.AfterCommitExecutor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境变量缓存（L1 本地 + Redis Pub/Sub 集群广播），执行期每次取值不查库。
 *
 * <p>敏感变量在加载时解密，内存中保存明文。</p>
 */
@Slf4j
@Component
public class EnvVariableCacheManager {

    public static final String REFRESH_TOPIC = "flow:sys:env:refresh:topic";

    public record CachedEnvVariable(String code, String value, boolean secret) {
    }

    private volatile Map<String, CachedEnvVariable> cache = new ConcurrentHashMap<>();

    @Resource
    private SysEnvVariableRepository sysEnvVariableRepository;

    @Resource
    private AesEncryptUtil aesEncryptUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        reloadAll();
    }

    public void reloadAll() {
        try {
            Map<String, CachedEnvVariable> next = new ConcurrentHashMap<>();
            for (SysEnvVariableDO row : sysEnvVariableRepository.findAll()) {
                CachedEnvVariable cached = toCached(row);
                if (cached != null) {
                    next.put(cached.code(), cached);
                }
            }
            this.cache = next;
            log.info("[EnvVariableCache] 环境变量加载完成，共 {} 个", next.size());
        } catch (Exception e) {
            log.error("[EnvVariableCache] 全量加载失败，保留旧缓存继续服务", e);
        }
    }

    public Optional<CachedEnvVariable> get(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(cache.get(code));
    }

    public boolean contains(String code) {
        return code != null && cache.containsKey(code);
    }

    /** 写库后立即更新本节点，避免广播到达前读到旧值 */
    public void putLocal(SysEnvVariableDO row) {
        CachedEnvVariable cached = toCached(row);
        if (cached != null) {
            cache.put(cached.code(), cached);
        }
    }

    public void removeLocal(String code) {
        if (code != null) {
            cache.remove(code);
        }
    }

    public void publishRefreshEvent() {
        AfterCommitExecutor.runOnce("env variable cache refresh", () -> {
            try {
                stringRedisTemplate.convertAndSend(REFRESH_TOPIC, "REFRESH");
            } catch (Exception e) {
                log.warn("[EnvVariableCache] 发布刷新事件失败，仅刷新本节点: {}", e.getMessage());
                reloadAll();
            }
        });
    }

    private CachedEnvVariable toCached(SysEnvVariableDO row) {
        if (row == null || row.getCode() == null) {
            return null;
        }
        boolean secret = Boolean.TRUE.equals(row.getSecret());
        String value = row.getValue();
        if (secret && value != null && !value.isEmpty()) {
            try {
                value = aesEncryptUtil.decrypt(value);
            } catch (Exception e) {
                // 密钥变更等导致解密失败时按「未配置」处理，执行期会明确报错而不是带着密文外发
                log.error("[EnvVariableCache] 敏感变量解密失败，按未配置处理: {}", row.getCode());
                return null;
            }
        }
        return new CachedEnvVariable(row.getCode(), value == null ? "" : value, secret);
    }
}
