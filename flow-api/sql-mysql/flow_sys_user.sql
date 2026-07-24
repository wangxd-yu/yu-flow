-- Table: flow_sys_user
-- 系统用户
CREATE TABLE IF NOT EXISTS `flow_sys_user` (
  `id` varchar(32) NOT NULL,
  `username` varchar(64) NOT NULL COMMENT '登录名',
  `password_hash` varchar(128) NOT NULL COMMENT 'BCrypt 密码',
  `display_name` varchar(64) COMMENT '显示名',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `is_builtin` tinyint NOT NULL DEFAULT 0 COMMENT '1内置不可删',
  `remark` varchar(255),
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';
