-- 安全配置组内顺序（开关置顶；若 V2026_07_23_06 已执行过则补齐）
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'RBAC_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'LOGIN_MAX_RETRY';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'LOGIN_LOCK_DURATION';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'TOKEN_EXPIRE';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'TOKEN_REFRESH_EXPIRE';
