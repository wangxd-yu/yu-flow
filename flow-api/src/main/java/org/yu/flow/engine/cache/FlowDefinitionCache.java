package org.yu.flow.engine.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.engine.evaluator.FlowParser;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.util.ContentHashUtil;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * DSL → {@link FlowDefinition} 编译缓存（含拓扑索引）。
 *
 * <p>key = DSL 内容 SHA-256。已发布任务/服务重复执行时命中缓存，跳过 parse + parentMap 构建。
 * 缓存对象只读共享，执行态写入 {@code ExecutionContext}，不得修改 Definition。
 */
@Slf4j
@Component
public class FlowDefinitionCache {

    private final Cache<String, FlowDefinition> cache;
    private final FlowParser parser = new FlowParser();

    public FlowDefinitionCache(YuFlowProperties properties) {
        YuFlowProperties.Engine engine = properties.getEngine();
        int maxSize = Math.max(16, engine.getDefinitionCacheMaxSize());
        long expireMinutes = Math.max(1, engine.getDefinitionCacheExpireMinutes());
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(Duration.ofMinutes(expireMinutes))
                .recordStats()
                .build();
        log.info("[FlowDefinitionCache] 已初始化: maxSize={}, expireAfterAccess={}min",
                maxSize, expireMinutes);
    }

    /**
     * 按 DSL 内容获取或解析 {@link FlowDefinition}（含拓扑索引）。
     */
    public FlowDefinition getOrParse(String dsl) throws JsonProcessingException {
        if (dsl == null || dsl.isBlank()) {
            throw new FlowException("DSL_EMPTY", "流程 DSL 为空");
        }
        String key = ContentHashUtil.sha256Hex(dsl);
        try {
            return cache.get(key, k -> {
                try {
                    long t0 = System.nanoTime();
                    FlowDefinition def = parser.parse(dsl);
                    def.ensureTopologyIndexes();
                    long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
                    log.debug("[FlowDefinitionCache] miss key={}… costMs={}",
                            key.substring(0, 8), costMs);
                    return def;
                } catch (JsonProcessingException e) {
                    throw new FlowException("DSL_PARSE_ERROR",
                            "流程 DSL 解析失败: " + e.getMessage(), e);
                }
            });
        } catch (FlowException e) {
            if (e.getCause() instanceof JsonProcessingException jpe) {
                throw jpe;
            }
            throw e;
        }
    }

    /** 主动失效（发布/回滚后可选调用；内容哈希变更时也会 miss） */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }
}
