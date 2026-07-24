-- Table: flow_log_service
-- 内部服务编排执行日志
CREATE TABLE IF NOT EXISTS `flow_log_service` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `service_id` varchar(32) NOT NULL COMMENT '关联服务ID',
  `service_name` varchar(128) COMMENT '服务名称（冗余）',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '触发类型：MANUAL=手动, CALL=流程内调用, DEBUG=调试',
  `status` varchar(16) NOT NULL COMMENT '执行状态：SUCCESS / FAILED / RUNNING',
  `cost_time_ms` bigint COMMENT '耗时（毫秒）',
  `error_msg` text COMMENT '失败信息',
  `trace_data` longtext COMMENT 'FlowTrace JSON 快照（logEnabled=true 时记录）',
  `create_time` datetime COMMENT '执行开始时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_service_create_time` (`create_time`),
  KEY `idx_flow_log_service_service_id` (`service_id`),
  KEY `idx_flow_log_service_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部服务编排执行日志';
