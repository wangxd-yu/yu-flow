-- Table: flow_log_open_call
-- 开放平台入站调用摘要
CREATE TABLE IF NOT EXISTS `flow_log_open_call` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `platform_id` varchar(32) COMMENT '开放平台ID',
  `app_key` varchar(64) COMMENT '调用方 AppKey',
  `api_id` varchar(32) COMMENT '命中的 API ID',
  `method` varchar(16) COMMENT 'HTTP 方法',
  `path` varchar(512) COMMENT '真实发布路径',
  `status` int COMMENT 'HTTP 状态',
  `cost_ms` bigint COMMENT '耗时毫秒',
  `error_code` varchar(64) COMMENT '业务/鉴权错误码',
  `request_id` varchar(64) COMMENT '请求追踪ID',
  `create_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_open_call_app_key` (`app_key`),
  KEY `idx_flow_log_open_call_create_time` (`create_time`),
  KEY `idx_flow_log_open_call_platform_id_create_time` (`platform_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台入站调用摘要';
