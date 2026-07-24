-- 运行告警（Webhook）：复用 metrics anomalies TopN
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `create_time`, `update_time`)
VALUES
    ('ALERT_ENABLED', 'false', 'BOOLEAN', 'ALERT',
     '运行告警总开关。开启后按间隔扫描近窗口异常并推送 Webhook', 1, 1, NOW(), NOW()),
    ('ALERT_WEBHOOK_URL', '', 'STRING', 'ALERT',
     'Webhook URL（钉钉/企微自定义机器人或任意 HTTP 接收端）', 1, 1, NOW(), NOW()),
    ('ALERT_INTERVAL_MINUTES', '15', 'NUMBER', 'ALERT',
     '扫描间隔（分钟）', 1, 1, NOW(), NOW()),
    ('ALERT_TOP_N', '10', 'NUMBER', 'ALERT',
     '每次最多推送异常条数', 1, 1, NOW(), NOW()),
    ('ALERT_WINDOW', '24h', 'STRING', 'ALERT',
     '指标窗口：1h / 24h / 7d', 1, 1, NOW(), NOW()),
    ('ALERT_MIN_HEALTH', 'error', 'STRING', 'ALERT',
     '最低告警健康度：error 仅严重；warn 含预警', 1, 1, NOW(), NOW()),
    ('ALERT_DEDUP_MINUTES', '60', 'NUMBER', 'ALERT',
     '同一资产告警去重静默（分钟）', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
