-- Table: flow_regression_suite
-- 回归测试套件
CREATE TABLE IF NOT EXISTS `flow_regression_suite` (
  `id` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `asset_type` varchar(32) NOT NULL COMMENT 'API|TASK|SERVICE',
  `asset_id` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_regression_suite_asset_type_asset_id` (`asset_type`, `asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归测试套件';
