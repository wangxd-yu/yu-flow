-- Table: flow_page_directory
-- 页面目录表
CREATE TABLE IF NOT EXISTS `flow_page_directory` (
  `id` varchar(64) NOT NULL COMMENT '主键（雪花ID）',
  `parent_id` varchar(64) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `sort` int DEFAULT 0 COMMENT '排序（升序）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_page_directory_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面目录表';
