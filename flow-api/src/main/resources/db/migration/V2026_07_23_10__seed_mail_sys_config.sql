-- SMTP 邮件全局配置（告警 / 后续流程编排共用；优先于 yu.flow.mail yml）
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `sort_order`, `create_time`, `update_time`)
VALUES
    ('MAIL_ENABLED', 'false', 'BOOLEAN', 'MAIL',
     '邮件发送总开关。关闭后告警 Email 通道与后续邮件节点均不可发信', 1, 1, 10, NOW(), NOW()),
    ('MAIL_HOST', '', 'STRING', 'MAIL',
     'SMTP 主机，如 smtp.qq.com / smtp.163.com', 1, 1, 20, NOW(), NOW()),
    ('MAIL_PORT', '465', 'NUMBER', 'MAIL',
     'SMTP 端口：SSL 常用 465，STARTTLS 常用 587', 1, 1, 30, NOW(), NOW()),
    ('MAIL_USERNAME', '', 'STRING', 'MAIL',
     'SMTP 登录账号（通常为邮箱地址）', 1, 1, 40, NOW(), NOW()),
    ('MAIL_PASSWORD', '', 'STRING', 'MAIL',
     'SMTP 密码或应用专用密码', 1, 1, 50, NOW(), NOW()),
    ('MAIL_FROM', '', 'STRING', 'MAIL',
     '发件人地址；为空则使用 MAIL_USERNAME', 1, 1, 60, NOW(), NOW()),
    ('MAIL_SSL', 'true', 'BOOLEAN', 'MAIL',
     '启用 SMTPS/SSL（465）', 1, 1, 70, NOW(), NOW()),
    ('MAIL_STARTTLS', 'false', 'BOOLEAN', 'MAIL',
     '启用 STARTTLS（587）；与 SSL 二选一为主', 1, 1, 80, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `remark` = VALUES(`remark`),
    `sort_order` = VALUES(`sort_order`);
