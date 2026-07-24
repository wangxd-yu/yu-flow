-- Table: flow_alert_channel
-- 告警通道
CREATE TABLE IF NOT EXISTS `flow_alert_channel` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '通道名称',
  `type` varchar(20) NOT NULL COMMENT 'WEBHOOK | EMAIL',
  `config_json` text COMMENT '通道配置 JSON',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_alert_channel_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警通道';
