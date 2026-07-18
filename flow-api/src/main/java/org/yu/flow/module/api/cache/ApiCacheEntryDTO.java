package org.yu.flow.module.api.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 某条生效中的 API 响应缓存条目（管理端查询用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCacheEntryDTO {

    /** Redis 完整 key */
    private String key;

    /** 剩余 TTL（秒），-1 表示永不过期，-2 表示不存在 */
    private long ttlSeconds;

    /** 缓存值字节长度（UTF-8） */
    private long sizeBytes;
}
