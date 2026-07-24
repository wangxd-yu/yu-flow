-- Table: flow_log_third
-- 第三方接口调用日志
CREATE TABLE IF NOT EXISTS `flow_log_third` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `api_type` varchar(50) COMMENT '接口标识',
  `source` varchar(16) COMMENT '调用来源：API/TASK/DEBUG/OTHER',
  `source_ref` varchar(64) COMMENT '来源关联ID（apiId/taskId）',
  `source_name` varchar(128) COMMENT '来源名称（接口名/任务名）',
  `request_url` varchar(512) COMMENT '请求URL',
  `request_method` varchar(10) COMMENT '请求方法',
  `request_params` text COMMENT '请求参数',
  `request_headers` text COMMENT '请求头',
  `response_status` int COMMENT '响应状态码',
  `response_body` text COMMENT '响应内容',
  `elapsed_time` bigint COMMENT '请求耗时(ms)',
  `is_success` tinyint COMMENT '是否成功（0失败/1成功）',
  `error_message` varchar(1000) COMMENT '错误信息',
  `curl` text COMMENT 'Curl命令',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_third_api_type` (`api_type`),
  KEY `idx_flow_log_third_create_time` (`create_time`),
  KEY `idx_flow_log_third_source` (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方接口调用日志';
