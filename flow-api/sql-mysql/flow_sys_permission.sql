-- Table: flow_sys_permission
-- 权限点
CREATE TABLE IF NOT EXISTS `flow_sys_permission` (
  `id` varchar(32) NOT NULL,
  `perm_code` varchar(128) NOT NULL COMMENT '如 flow:api:write',
  `perm_name` varchar(64) NOT NULL,
  `group_code` varchar(64) COMMENT '分组：flow/ops/infra/sys',
  `remark` varchar(255),
  `create_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_permission_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';
