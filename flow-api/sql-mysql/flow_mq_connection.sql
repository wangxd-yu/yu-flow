-- Table: flow_mq_connection
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
