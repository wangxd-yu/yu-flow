-- Table: flow_sys_role_permission
-- 角色-权限
CREATE TABLE IF NOT EXISTS `flow_sys_role_permission` (
  `role_id` varchar(32) NOT NULL,
  `perm_code` varchar(128) NOT NULL,
  PRIMARY KEY (`role_id`, `perm_code`),
  KEY `idx_flow_sys_role_permission_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';
