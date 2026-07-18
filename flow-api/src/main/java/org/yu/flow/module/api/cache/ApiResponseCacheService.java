package org.yu.flow.module.api.cache;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 动态 API 查询响应 Redis 缓存服务。
 *
 * <p>Key 规范：</p>
 * <ul>
 *   <li>{@code flow:api:resp:{apiId}:{hash}} — 包装后的响应 JSON</li>
 *   <li>{@code flow:api:resp:index:{apiId}} — 该接口所有缓存 key 的索引 SET</li>
 * </ul>
 *
 * <p>Redis 异常时 fail-open：读 miss、写忽略，不影响业务执行。</p>
 */
@Slf4j
@Service
public class ApiResponseCacheService {

    public static final String RESP_KEY_PREFIX = "flow:api:resp:";
    public static final String INDEX_KEY_PREFIX = "flow:api:resp:index:";

    private static final int DEFAULT_TTL_SECONDS = 300;
    private static final int MAX_TTL_SECONDS = 86400;
    /** 管理端查看缓存内容时的最大字符数，超出则截断（避免按字节截断破坏 UTF-8） */
    private static final int MAX_VIEW_CONTENT_CHARS = 2 * 1024 * 1024;

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper()
            .copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /**
     * 解析 cache_config JSON；无效或空时返回 null。
     */
    public ApiCacheConfig parseConfig(String cacheConfigJson) {
        if (StrUtil.isBlank(cacheConfigJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(cacheConfigJson, ApiCacheConfig.class);
        } catch (Exception e) {
            log.warn("[ApiResponseCache] 解析 cacheConfig 失败: {}", e.getMessage());
            return null;
        }
    }

    public boolean isEnabled(ApiCacheConfig config) {
        return config != null && config.isEnabled();
    }

    /**
     * 构建完整 Redis key。
     */
    public String buildCacheKey(String apiId, ApiCacheConfig config,
                                Map<String, Object> typedQuery,
                                Map<String, Object> typedBody,
                                Map<String, Object> typedPath,
                                Map<String, Object> typedHeaders,
                                Pageable pageable) {
        Map<String, Object> keyMaterial = buildKeyMaterial(
                config, typedQuery, typedBody, typedPath, typedHeaders, pageable);
        String hash = sha256Hex(stableJson(keyMaterial));
        return RESP_KEY_PREFIX + apiId + ":" + hash;
    }

    /**
     * 读取缓存 JSON；miss 或异常返回 null。
     */
    public String get(String cacheKey) {
        try {
            Object value = FlowRedisUtil.get(cacheKey);
            if (value == null) {
                return null;
            }
            return value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[ApiResponseCache] get 失败(fail-open): key={}, error={}", cacheKey, e.getMessage());
            return null;
        }
    }

    /**
     * 写入缓存并维护索引；异常吞掉（fail-open）。
     */
    public void put(String apiId, String cacheKey, String jsonBody, ApiCacheConfig config) {
        try {
            int ttl = normalizeTtl(config != null ? config.getTtlSeconds() : DEFAULT_TTL_SECONDS);
            FlowRedisUtil.set(cacheKey, jsonBody, ttl, TimeUnit.SECONDS);
            String indexKey = indexKey(apiId);
            FlowRedisUtil.sadd(indexKey, cacheKey);
            // 索引 TTL 取「现有剩余」与「本条 TTL」较大者，避免短 TTL 条目把索引提前冲掉
            long existingExpire = FlowRedisUtil.getExpire(indexKey, TimeUnit.SECONDS);
            long indexTtl = ttl;
            if (existingExpire > indexTtl) {
                indexTtl = existingExpire;
            }
            FlowRedisUtil.expire(indexKey, indexTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[ApiResponseCache] put 失败(fail-open): key={}, error={}", cacheKey, e.getMessage());
        }
    }

    /**
     * 清除某接口全部响应缓存。
     */
    public long evictAll(String apiId) {
        if (StrUtil.isBlank(apiId)) {
            return 0;
        }
        try {
            String indexKey = indexKey(apiId);
            Set<Object> members = FlowRedisUtil.smembers(indexKey);
            long deleted = 0;
            if (members != null) {
                for (Object member : members) {
                    if (member != null && FlowRedisUtil.delete(String.valueOf(member))) {
                        deleted++;
                    }
                }
            }
            FlowRedisUtil.delete(indexKey);
            return deleted;
        } catch (Exception e) {
            log.warn("[ApiResponseCache] evictAll 失败: apiId={}, error={}", apiId, e.getMessage());
            return 0;
        }
    }

    /**
     * 清除单条；key 必须属于该 apiId。
     */
    public boolean evictOne(String apiId, String cacheKey) {
        if (StrUtil.isBlank(apiId) || StrUtil.isBlank(cacheKey)) {
            return false;
        }
        String expectedPrefix = RESP_KEY_PREFIX + apiId + ":";
        if (!cacheKey.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("缓存 key 不属于该接口: " + cacheKey);
        }
        try {
            boolean deleted = FlowRedisUtil.delete(cacheKey);
            FlowRedisUtil.srem(indexKey(apiId), cacheKey);
            return deleted;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ApiResponseCache] evictOne 失败: key={}, error={}", cacheKey, e.getMessage());
            return false;
        }
    }

    /**
     * 按需读取单条缓存内容；key 必须属于该 apiId。
     * 内容过大时截断，避免管理端一次拉超大 JSON。
     */
    public ApiCacheContentDTO getEntryContent(String apiId, String cacheKey) {
        if (StrUtil.isBlank(apiId) || StrUtil.isBlank(cacheKey)) {
            throw new IllegalArgumentException("apiId / key 不能为空");
        }
        String expectedPrefix = RESP_KEY_PREFIX + apiId + ":";
        if (!cacheKey.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("缓存 key 不属于该接口: " + cacheKey);
        }
        try {
            long ttl = FlowRedisUtil.getExpire(cacheKey, TimeUnit.SECONDS);
            if (ttl == -2) {
                return null;
            }
            Object value = FlowRedisUtil.get(cacheKey);
            if (value == null) {
                return null;
            }
            String json = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            long sizeBytes = json.getBytes(StandardCharsets.UTF_8).length;
            boolean truncated = json.length() > MAX_VIEW_CONTENT_CHARS;
            String content = truncated
                    ? json.substring(0, MAX_VIEW_CONTENT_CHARS)
                    + "\n\n/* …内容过大，已截断（原始 " + sizeBytes + " bytes）… */"
                    : json;
            return ApiCacheContentDTO.builder()
                    .key(cacheKey)
                    .ttlSeconds(ttl)
                    .sizeBytes(sizeBytes)
                    .content(content)
                    .truncated(truncated)
                    .build();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ApiResponseCache] getEntryContent 失败: key={}, error={}", cacheKey, e.getMessage());
            throw new IllegalArgumentException("读取缓存内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出某接口当前生效缓存条目（不含 body）。
     */
    public List<ApiCacheEntryDTO> listEntries(String apiId) {
        if (StrUtil.isBlank(apiId)) {
            return Collections.emptyList();
        }
        try {
            String indexKey = indexKey(apiId);
            Set<Object> members = FlowRedisUtil.smembers(indexKey);
            if (members == null || members.isEmpty()) {
                return Collections.emptyList();
            }
            List<ApiCacheEntryDTO> result = new ArrayList<>();
            List<Object> stale = new ArrayList<>();
            for (Object member : members) {
                if (member == null) {
                    continue;
                }
                String key = String.valueOf(member);
                long ttl = FlowRedisUtil.getExpire(key, TimeUnit.SECONDS);
                if (ttl == -2) {
                    stale.add(member);
                    continue;
                }
                long sizeBytes = 0;
                Object value = FlowRedisUtil.get(key);
                if (value != null) {
                    String json = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
                    sizeBytes = json.getBytes(StandardCharsets.UTF_8).length;
                }
                result.add(ApiCacheEntryDTO.builder()
                        .key(key)
                        .ttlSeconds(ttl)
                        .sizeBytes(sizeBytes)
                        .build());
            }
            if (!stale.isEmpty()) {
                FlowRedisUtil.srem(indexKey, stale.toArray());
            }
            return result;
        } catch (Exception e) {
            log.warn("[ApiResponseCache] listEntries 失败: apiId={}, error={}", apiId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public String serializeConfig(ApiCacheConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("cacheConfig 序列化失败: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────── helpers ───────────────────────────

    private String indexKey(String apiId) {
        return INDEX_KEY_PREFIX + apiId;
    }

    private int normalizeTtl(int ttlSeconds) {
        if (ttlSeconds <= 0) {
            return DEFAULT_TTL_SECONDS;
        }
        return Math.min(ttlSeconds, MAX_TTL_SECONDS);
    }

    private Map<String, Object> buildKeyMaterial(ApiCacheConfig config,
                                                 Map<String, Object> typedQuery,
                                                 Map<String, Object> typedBody,
                                                 Map<String, Object> typedPath,
                                                 Map<String, Object> typedHeaders,
                                                 Pageable pageable) {
        Map<String, Object> material = new TreeMap<>();
        List<ApiCacheConfig.KeyParam> keyParams = config.getKeyParams() != null
                ? config.getKeyParams() : Collections.emptyList();
        for (ApiCacheConfig.KeyParam kp : keyParams) {
            if (kp == null || StrUtil.isBlank(kp.getSource()) || StrUtil.isBlank(kp.getName())) {
                continue;
            }
            String source = kp.getSource().trim().toLowerCase();
            String name = kp.getName().trim();
            Object value = resolveParam(source, name, typedQuery, typedBody, typedPath, typedHeaders);
            material.put(source + "." + name, value);
        }
        if (config.isIncludePageable() && pageable != null) {
            Map<String, Object> pageMap = new LinkedHashMap<>();
            pageMap.put("page", pageable.getPageNumber());
            pageMap.put("size", pageable.getPageSize());
            if (pageable.getSort() != null && pageable.getSort().isSorted()) {
                List<String> sorts = new ArrayList<>();
                for (Sort.Order order : pageable.getSort()) {
                    sorts.add(order.getProperty() + "," + order.getDirection().name());
                }
                Collections.sort(sorts);
                pageMap.put("sort", sorts);
            }
            material.put("__pageable__", pageMap);
        }
        return material;
    }

    private Object resolveParam(String source, String name,
                                Map<String, Object> typedQuery,
                                Map<String, Object> typedBody,
                                Map<String, Object> typedPath,
                                Map<String, Object> typedHeaders) {
        Map<String, Object> map;
        switch (source) {
            case "query":
            case "params":
                map = typedQuery;
                break;
            case "body":
                map = typedBody;
                break;
            case "path":
            case "pathparams":
                map = typedPath;
                break;
            case "header":
            case "headers":
                map = typedHeaders;
                // header 名大小写不敏感
                if (map != null) {
                    for (Map.Entry<String, Object> e : map.entrySet()) {
                        if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                            return e.getValue();
                        }
                    }
                }
                return null;
            default:
                return null;
        }
        return map != null ? map.get(name) : null;
    }

    private String stableJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            // 取前 16 字节 = 32 hex chars
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

}
