-- MQ 输入输出节点：连接配置 / MQ 任务 / 执行日志三张表 + flow:mq 权限种子
-- 背景：新增 mqTrigger 入口节点与 mqSend 输出节点，配套 MQ 连接管理与 MQ 任务资产

-- MQ 连接配置（RabbitMQ / Kafka）
CREATE TABLE IF NOT EXISTS `flow_mq_connection` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '连接名称',
  `code` varchar(64) NOT NULL COMMENT '连接编码（未删除记录内唯一），流程 DSL / MQ 任务通过 code 引用',
  `mq_type` varchar(32) NOT NULL COMMENT 'MQ 类型：RABBITMQ / KAFKA',
  `servers` varchar(512) NOT NULL COMMENT '服务器地址 host:port，多个逗号分隔（Kafka 即 bootstrap.servers）',
  `virtual_host` varchar(128) COMMENT '虚拟主机（仅 RabbitMQ，默认 /）',
  `username` varchar(128) COMMENT '用户名（可空；Kafka 有值时启用 SASL/PLAIN）',
  `password` varchar(512) COMMENT '密码（AES 密文存储）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `health_status` varchar(32) COMMENT '健康状态：HEALTHY / UNHEALTHY / UNKNOWN',
  `last_error_msg` varchar(1024) COMMENT '最近一次连接测试错误信息',
  `last_test_time` datetime COMMENT '最近一次连接测试时间',
  `info` varchar(512) COMMENT '备注描述',
  `deleted` int NOT NULL DEFAULT 0 COMMENT '软删除：0=正常, 1=已删除',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_mq_connection_code` (`code`),
  KEY `idx_flow_mq_connection_enabled` (`enabled`),
  KEY `idx_flow_mq_connection_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 连接配置';

-- MQ 任务定义（mqTrigger 入口流程资产）
CREATE TABLE IF NOT EXISTS `flow_mq_task_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '任务名称',
  `directory_id` varchar(32) COMMENT '关联目录ID（复用全局目录树）',
  `connection_code` varchar(64) NOT NULL COMMENT '绑定 MQ 连接编码（flow_mq_connection.code）',
  `topic` varchar(255) NOT NULL COMMENT '订阅 topic / 队列名',
  `consumer_group` varchar(128) COMMENT '消费组（Kafka group.id；Rabbit 忽略）',
  `concurrency` int NOT NULL DEFAULT 1 COMMENT '消费并发数',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
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
  KEY `idx_flow_mq_task_info_create_time` (`create_time`),
  KEY `idx_flow_mq_task_info_directory_id` (`directory_id`),
  KEY `idx_flow_mq_task_info_enabled` (`enabled`),
  KEY `idx_flow_mq_task_info_publish_status` (`publish_status`),
  KEY `idx_flow_mq_task_info_connection_code` (`connection_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 任务定义';

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
  `trace_data` longtext COMMENT 'FlowTrace JSON 快照（logEnabled=true 时记录）',
  `create_time` datetime COMMENT '执行开始时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_mq_task_log_create_time` (`create_time`),
  KEY `idx_flow_mq_task_log_status` (`status`),
  KEY `idx_flow_mq_task_log_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 任务执行日志';

-- 权限种子：flow:mq:view / flow:mq:write（ADMIN 持有 * 通配无需单独授予）
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
('p_mq_v', 'flow:mq:view', 'MQ查看', 'flow', 'MQ 连接与 MQ 任务查看', NOW()),
('p_mq_w', 'flow:mq:write', 'MQ编排', 'flow', 'MQ 连接与 MQ 任务编辑/发布/启停', NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- OPERATOR 授予查看+编排；VIEWER 仅查看
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
('role_operator', 'flow:mq:view'),
('role_operator', 'flow:mq:write'),
('role_viewer', 'flow:mq:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);
