-- Table: flow_sys_env_variable
-- 环境变量（每个环境各自维护，值不随发布包迁移）
CREATE TABLE IF NOT EXISTS `flow_sys_env_variable` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '变量名（大写字母开头，仅大写字母/数字/下划线），编排中以 $.env.CODE / ${env.CODE} 引用',
  `var_value` varchar(4000) COMMENT '变量值；secret=1 时为 AES 密文',
  `secret` tinyint(1) NOT NULL DEFAULT 0 COMMENT '敏感变量：0=否, 1=是（页面掩码显示，执行轨迹与三方日志脱敏）',
  `remark` varchar(512) COMMENT '说明（随发布包导出，提示目标环境该填什么）',
  `create_by` varchar(64) COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新人',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_env_variable_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境变量';
