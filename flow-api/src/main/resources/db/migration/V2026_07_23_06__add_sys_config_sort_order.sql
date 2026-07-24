-- 系统配置组内排序
ALTER TABLE `flow_sys_config`
    ADD COLUMN `sort_order` INT NOT NULL DEFAULT 100 COMMENT '组内展示顺序，越小越靠前' AFTER `status`;

-- 运行告警：开关置顶
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'ALERT_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'ALERT_WEBHOOK_URL';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'ALERT_INTERVAL_MINUTES';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'ALERT_TOP_N';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'ALERT_WINDOW';
UPDATE `flow_sys_config` SET `sort_order` = 60 WHERE `config_key` = 'ALERT_MIN_HEALTH';
UPDATE `flow_sys_config` SET `sort_order` = 70 WHERE `config_key` = 'ALERT_DEDUP_MINUTES';

-- 入站防护：开关置顶
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'INGRESS_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'INGRESS_DEFAULT_AUTH_MODE';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'INGRESS_DEFAULT_ANTI_REPLAY';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'INGRESS_DEFAULT_RATE_LIMIT_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'INGRESS_DEFAULT_RATE_LIMIT_QPS';
UPDATE `flow_sys_config` SET `sort_order` = 60 WHERE `config_key` = 'INGRESS_DEFAULT_IP_ALLOWLIST';
UPDATE `flow_sys_config` SET `sort_order` = 70 WHERE `config_key` = 'INGRESS_RATE_LIMIT_FAIL_OPEN';
UPDATE `flow_sys_config` SET `sort_order` = 80 WHERE `config_key` = 'INGRESS_DEFAULT_TIMEOUT_MS';

-- 开放平台：开关置顶
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'OPEN_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'OPEN_ALLOW_PLAIN_SECRET';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'OPEN_ALLOW_DIRECT_PATH';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'OPEN_REQUIRE_HOST_AUTH';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'OPEN_CALL_LOG_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 60 WHERE `config_key` = 'OPEN_SKEW_SECONDS';
UPDATE `flow_sys_config` SET `sort_order` = 70 WHERE `config_key` = 'OPEN_NONCE_FAIL_CLOSED';
UPDATE `flow_sys_config` SET `sort_order` = 80 WHERE `config_key` = 'OPEN_INCLUDE_BODY_HASH';
UPDATE `flow_sys_config` SET `sort_order` = 90 WHERE `config_key` = 'OPEN_ROTATE_GRACE_HOURS';

-- 安全配置：RBAC 开关置顶，其次登录/Token
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'RBAC_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'LOGIN_MAX_RETRY';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'LOGIN_LOCK_DURATION';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'TOKEN_EXPIRE';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'TOKEN_REFRESH_EXPIRE';
