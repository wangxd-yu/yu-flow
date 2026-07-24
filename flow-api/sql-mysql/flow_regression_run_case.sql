-- Table: flow_regression_run_case
-- 回归用例运行明细
CREATE TABLE IF NOT EXISTS `flow_regression_run_case` (
  `id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `case_id` varchar(64) NOT NULL,
  `case_name` varchar(100),
  `status` varchar(20) NOT NULL COMMENT 'PASSED|FAILED|ERROR|SKIPPED',
  `duration_ms` bigint,
  `message` varchar(500),
  `detail_json` varchar(2000) COMMENT '截断后的摘要，不含全量响应',
  PRIMARY KEY (`id`),
  KEY `idx_flow_regression_run_case_run_id` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归用例运行明细';
