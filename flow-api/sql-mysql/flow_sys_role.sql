-- Table: flow_sys_role
-- 系统角色
CREATE TABLE IF NOT EXISTS `flow_sys_role` (
  `id` varchar(32) NOT NULL,
  `role_code` varchar(64) NOT NULL COMMENT 'ADMIN/OPERATOR/VIEWER',
  `role_name` varchar(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `is_builtin` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(255),
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_role_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';
