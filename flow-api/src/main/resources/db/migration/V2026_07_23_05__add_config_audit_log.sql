-- 配置变更审计日志
CREATE TABLE IF NOT EXISTS `flow_log_audit` (
    `id`          VARCHAR(64)  NOT NULL,
    `action`      VARCHAR(64)  NOT NULL COMMENT 'API_PUBLISH|SYS_CONFIG_UPDATE|OPEN_SECRET_ROTATE',
    `operator`    VARCHAR(100) NULL COMMENT '操作人',
    `target_type` VARCHAR(64)  NULL COMMENT 'API|SYS_CONFIG|OPEN_CREDENTIAL',
    `target_id`   VARCHAR(64)  NULL,
    `detail`      VARCHAR(1024) NULL COMMENT 'JSON 摘要，不含密钥',
    `create_time` DATETIME     NULL,
    PRIMARY KEY (`id`),
    KEY `idx_audit_action_time` (`action`, `create_time`),
    KEY `idx_audit_operator` (`operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置变更审计';
