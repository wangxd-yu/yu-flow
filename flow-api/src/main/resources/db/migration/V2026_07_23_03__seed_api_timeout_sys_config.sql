-- 接口级执行超时：全局默认（毫秒）；≤0 不限制；接口 securityConfig.timeoutMs 可覆盖
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `create_time`, `update_time`)
VALUES
    ('INGRESS_DEFAULT_TIMEOUT_MS', '30000', 'NUMBER', 'INGRESS',
     '已发布 API 默认执行超时（毫秒）。≤0 不限制。接口 securityConfig.timeoutMs 可覆盖，改完需发布', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
