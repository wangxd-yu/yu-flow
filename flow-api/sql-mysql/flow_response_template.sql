-- Table: flow_response_template
-- API 响应模板表
CREATE TABLE IF NOT EXISTS `flow_response_template` (
  `id` varchar(32) NOT NULL COMMENT '主键 (雪花ID)',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `success_wrapper` text COMMENT '成功包装体 JSON 模板',
  `page_wrapper` text COMMENT '分页包装体 JSON 模板',
  `fail_wrapper` text COMMENT '失败包装体 JSON 模板',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否全局默认 (1:默认 0:非默认)',
  `remark` varchar(500) COMMENT '备注说明',
  `create_by` varchar(64) COMMENT '创建者',
  `create_time` datetime COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新者',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_response_template_template_name` (`template_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 响应模板表';
