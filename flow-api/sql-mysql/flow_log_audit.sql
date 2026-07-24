-- Table: flow_log_audit
-- 配置变更审计
CREATE TABLE IF NOT EXISTS `flow_log_audit` (
  `id` varchar(64) NOT NULL,
  `action` varchar(64) NOT NULL COMMENT 'API_PUBLISH|SYS_CONFIG_UPDATE|OPEN_SECRET_ROTATE',
  `operator` varchar(100) COMMENT '操作人',
  `target_type` varchar(64) COMMENT 'API|SYS_CONFIG|OPEN_CREDENTIAL',
  `target_id` varchar(64),
  `detail` varchar(1024) COMMENT 'JSON 摘要，不含密钥',
  `create_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_audit_action_create_time` (`action`, `create_time`),
  KEY `idx_flow_log_audit_operator` (`operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置变更审计';
