-- 动态 API 查询响应 Redis 缓存配置（JSON）

ALTER TABLE flow_api_info
    ADD COLUMN cache_config TEXT NULL COMMENT '响应缓存配置 JSON：enabled/ttlSeconds/keyParams/includePageable';
