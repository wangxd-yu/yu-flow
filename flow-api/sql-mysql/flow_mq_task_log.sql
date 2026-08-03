-- Table: flow_mq_task_log
-- MQ 任务执行日志
CREATE TABLE IF NOT EXISTS `flow_mq_task_log` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `task_id` varchar(32) NOT NULL COMMENT '关联 MQ 任务ID',
  `task_name` varchar(128) COMMENT '任务名称（冗余）',
  `topic` varchar(255) COMMENT '消息 topic / 队列名',
  `message_id` varchar(255) COMMENT '消息ID（幂等去重键）',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'MQ' COMMENT '触发类型：MQ=消息触发, MANUAL=手动',
  `status` varchar(16) NOT NULL COMMENT '执行状态：SUCCESS / FAILED / SKIPPED / RUNNING',
  `cost_time_ms` bigint COMMENT '耗时（毫秒）',
  `error_msg` text COMMENT '失败信息',
  `message_body` longtext COMMENT '原始消息体（JSON 解析前的字符串，支持超限截断）',
  `message_headers` text COMMENT '消息头 JSON 字典',
  `trace_data` longtext COMMENT 'FlowTrace JSON 快照（logMode=ALL 时记录）',
  `create_time` datetime COMMENT '执行开始时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_mq_task_log_create_time` (`create_time`),
  KEY `idx_flow_mq_task_log_status` (`status`),
  KEY `idx_flow_mq_task_log_task_id` (`task_id`),
  KEY `idx_flow_mq_task_log_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 任务执行日志';
