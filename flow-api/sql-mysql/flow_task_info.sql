-- Table: flow_task_info
-- 定时任务定义
CREATE TABLE IF NOT EXISTS `flow_task_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '任务名称',
  `directory_id` varchar(32) COMMENT '关联目录ID（复用全局目录树）',
  `cron` varchar(64) NOT NULL COMMENT 'Cron 表达式，如 0/5 * * * * ?',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
  `log_retention_days` int COMMENT '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数',
  `dsl_content` mediumtext COMMENT '流程定义 DSL JSON（草稿）',
  `publish_status` tinyint NOT NULL DEFAULT 0 COMMENT '发布状态：0=未发布，1=已发布',
  `published_snapshot` mediumtext COMMENT '发布快照 JSON：dslContent',
  `publish_time` datetime COMMENT '最近发布时间',
  `info` varchar(512) COMMENT '任务描述',
  `tags` varchar(255) COMMENT '标签，英文逗号分隔',
  `deleted` int NOT NULL DEFAULT 0 COMMENT '软删除：0=正常, 1=已删除',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_task_info_create_time` (`create_time`),
  KEY `idx_flow_task_info_directory_id` (`directory_id`),
  KEY `idx_flow_task_info_enabled` (`enabled`),
  KEY `idx_flow_task_info_publish_status` (`publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务定义';
