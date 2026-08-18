-- Table: flow_log_alert
-- 告警事件历史
CREATE TABLE IF NOT EXISTS `flow_log_alert` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `rule_id` varchar(64) COMMENT '规则 ID，SysConfig 兜底为空',
  `rule_name` varchar(100),
  `fingerprint` varchar(200) COMMENT '去重指纹',
  `asset_type` varchar(32),
  `asset_id` varchar(64),
  `asset_name` varchar(200),
  `health` varchar(20),
  `error_rate` double,
  `fail_count` bigint,
  `window` varchar(20),
  `channel_type` varchar(20),
  `channel_id` varchar(64),
  `status` varchar(20) NOT NULL COMMENT 'SUCCESS|FAIL|SUPPRESSED',
  `payload_json` text,
  `error_msg` varchar(500),
  `fired_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_alert_fired_at` (`fired_at`),
  KEY `idx_flow_log_alert_rule_id` (`rule_id`),
  KEY `idx_flow_log_alert_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件历史';
