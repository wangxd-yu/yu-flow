-- Table: flow_model_info
-- 数据模型信息表
CREATE TABLE IF NOT EXISTS `flow_model_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `directory_id` varchar(32) COMMENT '关联全局目录树',
  `name` varchar(256) NOT NULL COMMENT '模型中文名，如：用户信息',
  `table_name` varchar(256) NOT NULL COMMENT '底层物理表名，如：t_user',
  `fields_schema` longtext COMMENT '核心元数据 JSON 数组（包含字段名、类型、UI配置等）',
  `status` tinyint DEFAULT 0 COMMENT '状态：0=停用，1=启用',
  `datasource` varchar(50) COMMENT '关联的动态数据源 code',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_model_info_directory_id` (`directory_id`),
  KEY `idx_flow_model_info_status` (`status`),
  UNIQUE KEY `uk_flow_model_info_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型信息表';
