-- Table: flow_alert_rule
-- 告警规则
CREATE TABLE IF NOT EXISTS `flow_alert_rule` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '规则名称',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `scope_asset_types` varchar(200) COMMENT '资产类型 CSV：API,TASK,SERVICE,PLATFORM；空=全部',
  `window` varchar(20) NOT NULL DEFAULT '24h' COMMENT '1h/24h/7d',
  `min_health` varchar(20) NOT NULL DEFAULT 'error' COMMENT 'error|warn',
  `top_n` int NOT NULL DEFAULT 10,
  `channel_ids` varchar(500) COMMENT '通道 ID JSON 数组',
  `interval_minutes` int NOT NULL DEFAULT 15,
  `dedup_minutes` int NOT NULL DEFAULT 60,
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_alert_rule_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则';
