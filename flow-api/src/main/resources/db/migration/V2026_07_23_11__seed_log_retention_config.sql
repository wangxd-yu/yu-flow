-- 日志定时清理保留天数（LOG 分组；0 = 不清理）
-- 补齐 Task / Service / Third / Open / Audit / Alert；并为既有键写入 sort_order

INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `sort_order`, `create_time`, `update_time`)
VALUES
    ('LOG_EXECUTION_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     'API 执行日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 10, NOW(), NOW()),
    ('LOG_LOGIN_RETENTION_DAYS', '90', 'NUMBER', 'LOG',
     '登录审计日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 20, NOW(), NOW()),
    ('LOG_TASK_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '定时任务执行日志保留天数（flow_log_task；0 = 不清理）', 1, 1, 30, NOW(), NOW()),
    ('LOG_SERVICE_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '服务编排执行日志保留天数（flow_log_service；0 = 不清理）', 1, 1, 40, NOW(), NOW()),
    ('LOG_THIRD_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '第三方调用日志保留天数（flow_log_third；0 = 不清理）', 1, 1, 50, NOW(), NOW()),
    ('LOG_OPEN_CALL_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '开放平台调用日志保留天数（flow_log_open_call；0 = 不清理）', 1, 1, 60, NOW(), NOW()),
    ('LOG_AUDIT_RETENTION_DAYS', '180', 'NUMBER', 'LOG',
     '配置变更审计日志保留天数（flow_log_audit；0 = 不清理）', 1, 1, 70, NOW(), NOW()),
    ('LOG_ALERT_EVENT_RETENTION_DAYS', '90', 'NUMBER', 'LOG',
     '告警历史事件保留天数（flow_alert_event；0 = 不清理）', 1, 1, 80, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `remark` = VALUES(`remark`),
    `sort_order` = VALUES(`sort_order`);
