-- Table: flow_env
-- 发布逻辑环境
CREATE TABLE IF NOT EXISTS `flow_env` (
  `id` varchar(32) NOT NULL COMMENT '主键（固定码如 env_dev）',
  `code` varchar(32) NOT NULL COMMENT 'DEV|STAGING|PROD',
  `name` varchar(100) NOT NULL COMMENT '显示名',
  `require_suite_pass` tinyint NOT NULL DEFAULT 0 COMMENT '1=发布前需回归通过',
  `pass_ttl_hours` int NOT NULL DEFAULT 24 COMMENT '通过结果有效小时数',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用：0=否, 1=是',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `remark` varchar(512) COMMENT '备注',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_env_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布逻辑环境';
