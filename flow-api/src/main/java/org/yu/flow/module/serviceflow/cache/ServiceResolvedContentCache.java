package org.yu.flow.module.serviceflow.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.util.ContentHashUtil;

import java.time.Duration;

/**
 * 服务 CALL 用 ResolvedContent 缓存。
 *
 * <p><b>分布式安全：</b>key = publishedSnapshot 正文 SHA-256（内容寻址）。
 * 发布后快照变 → hash 变 → 自然 miss，无需 Redis Pub/Sub。</p>
 */
@Slf4j
@Component
public class ServiceResolvedContentCache {

    private static final ObjectMapper MAPPER = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper();

    private final Cache<String, ResolvedContent> cache;

    public ServiceResolvedContentCache(YuFlowProperties properties) {
        int max = Math.max(16, properties.getEngine().getResolvedContentCacheMaxSize());
        this.cache = Caffeine.newBuilder()
                .maximumSize(max)
                .expireAfterAccess(Duration.ofMinutes(60))
                .recordStats()
                .build();
        log.info("[ServiceResolvedContentCache] 已初始化: maxSize={}", max);
    }

    /**
     * 解析已发布快照；失败返回 null（由调用方降级草稿）。
     */
    public ResolvedContent getOrParsePublished(String publishedSnapshot) {
        if (publishedSnapshot == null || publishedSnapshot.isBlank()) {
            return null;
        }
        String key = ContentHashUtil.sha256Hex(publishedSnapshot);
        ResolvedContent hit = cache.getIfPresent(key);
        if (hit != null) {
            return hit;
        }
        ResolvedContent parsed = parse(publishedSnapshot);
        if (parsed != null) {
            cache.put(key, parsed);
        }
        return parsed;
    }

    private static ResolvedContent parse(String publishedSnapshot) {
        try {
            JsonNode snap = MAPPER.readTree(publishedSnapshot);
            String dsl = snap.path("dslContent").asText(null);
            String contract = snap.path("contract").asText(null);
            if (dsl == null || dsl.isBlank()) {
                return null;
            }
            return new ResolvedContent(dsl, contract);
        } catch (Exception e) {
            log.warn("[ServiceResolvedContentCache] 解析 publishedSnapshot 失败: {}", e.getMessage());
            return null;
        }
    }

    public record ResolvedContent(String dslContent, String contract) {
    }
}
