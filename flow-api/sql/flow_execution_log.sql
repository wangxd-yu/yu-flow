CREATE TABLE `flow_execution_log` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `api_id` varchar(32) DEFAULT NULL COMMENT 'API ID',
  `api_name` varchar(128) DEFAULT NULL COMMENT 'API名称',
  `url` varchar(255) DEFAULT NULL COMMENT '请求URL',
  `service_type` varchar(32) DEFAULT NULL COMMENT '接口类型: FLOW/DB/JSON/STRING',
  `method` varchar(16) DEFAULT NULL COMMENT '请求方法',
  `request_params` longtext COMMENT '请求参数快照(JSON)',
  `response_body` longtext COMMENT '返回结果快照(JSON)',
  `status` varchar(16) DEFAULT NULL COMMENT '执行状态: SUCCESS/ERROR',
  `error_msg` longtext COMMENT '错误信息',
  `cost_time_ms` bigint(20) DEFAULT NULL COMMENT '耗时(毫秒)',
  `trace_data` longtext COMMENT '完整追踪快照(如有)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_api_id` (`api_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API执行日志表';
