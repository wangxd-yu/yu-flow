-- ═══════════════════════════════════════════════════════════════════════════
--  Seed log retention configuration into flow_sys_config
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO flow_sys_config (id, config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('log_retention_exec', 'LOG_EXECUTION_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     'API 执行日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, NOW(), NOW()),
    ('log_retention_login', 'LOG_LOGIN_RETENTION_DAYS', '90', 'NUMBER', 'LOG',
     '登录审计日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE config_key = config_key;
