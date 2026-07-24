-- Table: flow_api_excel_template
-- API Excel 导出模板
CREATE TABLE IF NOT EXISTS `flow_api_excel_template` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `api_id` varchar(64) NOT NULL COMMENT '接口 ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `content_type` varchar(120) COMMENT 'MIME',
  `content` mediumblob NOT NULL COMMENT 'xlsx 二进制',
  `file_size` int NOT NULL DEFAULT 0,
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_api_excel_template_api_id` (`api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Excel 导出模板';
