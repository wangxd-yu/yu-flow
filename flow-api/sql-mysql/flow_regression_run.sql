-- Table: flow_regression_run
-- 回归运行记录
CREATE TABLE IF NOT EXISTS `flow_regression_run` (
  `id` varchar(64) NOT NULL,
  `suite_id` varchar(64) NOT NULL,
  `asset_type` varchar(32) NOT NULL,
  `asset_id` varchar(64) NOT NULL,
  `env_code` varchar(32) NOT NULL,
  `status` varchar(20) NOT NULL COMMENT 'RUNNING|PASSED|FAILED|ERROR',
  `total_cases` int NOT NULL DEFAULT 0,
  `passed_cases` int NOT NULL DEFAULT 0,
  `failed_cases` int NOT NULL DEFAULT 0,
  `started_at` datetime NOT NULL,
  `finished_at` datetime,
  `triggered_by` varchar(100),
  `summary` varchar(500),
  PRIMARY KEY (`id`),
  KEY `idx_flow_regression_run_asset_type_asset_id_env_code_finished_at` (`asset_type`, `asset_id`, `env_code`, `finished_at`),
  KEY `idx_flow_regression_run_suite_id` (`suite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归运行记录';
