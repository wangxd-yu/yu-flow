-- Table: flow_directory
-- 全局目录表
CREATE TABLE IF NOT EXISTS `flow_directory` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `parent_id` varchar(32) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `biz_type` varchar(32) COMMENT '业务域：api/task/service/model/page/mqtask，空=共用',
  `sort` int DEFAULT 0 COMMENT '排序（升序）',
  `path_prefix` varchar(256) DEFAULT NULL COMMENT 'URL路径前缀，可空；新建接口默认继承',
  `security_config` text COMMENT '目录级入站防护JSON，结构同ApiSecurityConfig',
  `privacy_config` text COMMENT '目录级出站隐私JSON，结构同ApiPrivacyConfig',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_directory_biz_type` (`biz_type`),
  KEY `idx_flow_directory_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局目录表';
