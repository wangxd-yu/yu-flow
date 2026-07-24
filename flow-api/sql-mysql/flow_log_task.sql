-- Table: flow_log_task
-- 定时任务执行日志
CREATE TABLE IF NOT EXISTS `flow_log_task` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `task_id` varchar(32) NOT NULL COMMENT '关联任务ID',
  `task_name` varchar(128) COMMENT '任务名称（冗余）',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'CRON' COMMENT '触发类型：CRON=定时, MANUAL=手动',
  `status` varchar(16) NOT NULL COMMENT '执行状态：SUCCESS / FAILED / RUNNING',
  `cost_time_ms` bigint COMMENT '耗时（毫秒）',
  `error_msg` text COMMENT '失败信息',
  `trace_data` longtext COMMENT 'FlowTrace JSON 快照（logEnabled=true 时记录）',
  `create_time` datetime COMMENT '执行开始时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_task_create_time` (`create_time`),
  KEY `idx_flow_log_task_status` (`status`),
  KEY `idx_flow_log_task_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行日志';
