-- Table: flow_datasource
-- 动态数据源配置表
CREATE TABLE IF NOT EXISTS `flow_datasource` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `code` varchar(50) COMMENT '数据源全局唯一编码，用于跨环境关联',
  `name` varchar(100) NOT NULL COMMENT '数据源名称',
  `db_type` varchar(20) NOT NULL COMMENT '数据库类型(mysql/postgresql/highgo)',
  `driver_class_name` varchar(200) NOT NULL COMMENT '驱动类名',
  `url` varchar(500) NOT NULL COMMENT 'JDBC URL',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `initial_size` int DEFAULT 5 COMMENT '初始连接数',
  `min_idle` int DEFAULT 5 COMMENT '最小空闲连接',
  `max_active` int DEFAULT 20 COMMENT '最大活动连接',
  `status` tinyint DEFAULT 1 COMMENT '状态(0-停用,1-启用)',
  `wall_config` text COMMENT 'SQL安全墙JSON(DataSourceWallConfig)',
  `is_system` tinyint NOT NULL DEFAULT 0 COMMENT '系统数据源(1=不可删改连接，如[DEFAULT])',
  `health_status` varchar(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '连接健康度：HEALTHY-健康, UNHEALTHY-异常, UNKNOWN-未知',
  `error_count` int NOT NULL DEFAULT 0 COMMENT '连续连接失败次数',
  `last_error_msg` text COMMENT '最后一次连接失败的异常堆栈/简述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_datasource_name` (`name`),
  UNIQUE KEY `uk_flow_datasource_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态数据源配置表';
