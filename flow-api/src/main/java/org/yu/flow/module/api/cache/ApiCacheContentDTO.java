package org.yu.flow.module.api.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条响应缓存内容（管理端按需查看）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCacheContentDTO {

    /** Redis 完整 key */
    private String key;

    /** 剩余 TTL（秒），-1 表示永不过期，-2 表示不存在 */
    private long ttlSeconds;

    /** 原始缓存值字节长度（UTF-8） */
    private long sizeBytes;

    /** 缓存内容（可能因过大被截断） */
    private String content;

    /** content 是否已截断 */
    private boolean truncated;
}
