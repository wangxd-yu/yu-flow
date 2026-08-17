-- Table: flow_page_info
-- 页面信息表
CREATE TABLE IF NOT EXISTS `flow_page_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `directory_id` varchar(32) COMMENT '关联全局目录树',
  `name` varchar(256) NOT NULL COMMENT '页面名称',
  `route_path` varchar(512) NOT NULL COMMENT '访问路径（唯一）',
  `json` longtext COMMENT '页面配置 JSON Schema（Amis）',
  `status` tinyint DEFAULT 0 COMMENT '状态：0=草稿，1=已发布',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_page_info_directory_id` (`directory_id`),
  KEY `idx_flow_page_info_status` (`status`),
  UNIQUE KEY `uk_flow_page_info_route_path` (`route_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面信息表';
