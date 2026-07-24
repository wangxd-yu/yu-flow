-- 已发布 API 入站防护（按接口覆盖全局 yu.flow.ingress）
ALTER TABLE `flow_api_info`
    ADD COLUMN `security_config` TEXT NULL COMMENT '入站防护 JSON：authMode/antiReplay/rateLimit/ipAllowlist' AFTER `cache_config`;
