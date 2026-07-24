-- Table: flow_regression_case
-- 回归用例
CREATE TABLE IF NOT EXISTS `flow_regression_case` (
  `id` varchar(64) NOT NULL,
  `suite_id` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `headers_json` text COMMENT '请求头 JSON（禁止敏感头）',
  `query_json` text COMMENT 'Query JSON',
  `body` mediumtext COMMENT '请求体，≤32KB',
  `expect_trace_status` varchar(20) COMMENT 'success|error，空则不校验',
  `expect_json_path` varchar(128) COMMENT '简单 JSONPath',
  `expect_value` varchar(500) COMMENT '期望值（字符串比较）',
  `timeout_ms` int NOT NULL DEFAULT 10000,
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_regression_case_suite_id` (`suite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归用例';
