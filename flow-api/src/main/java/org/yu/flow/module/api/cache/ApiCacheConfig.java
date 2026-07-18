package org.yu.flow.module.api.cache;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态 API 查询响应缓存配置。
 *
 * <p>对应 {@code flow_api_info.cache_config} JSON。</p>
 */
@Data
public class ApiCacheConfig {

    /** 是否启用响应缓存 */
    private boolean enabled;

    /** TTL（秒），默认 300，上限 86400 */
    private int ttlSeconds = 300;

    /** 参与缓存 key 的请求参数 */
    private List<KeyParam> keyParams = new ArrayList<>();

    /** 是否把 page/size/sort 计入 key */
    private boolean includePageable = true;

    @Data
    public static class KeyParam {
        /** query / body / path / header */
        private String source;
        /** 参数名 */
        private String name;
    }
}
