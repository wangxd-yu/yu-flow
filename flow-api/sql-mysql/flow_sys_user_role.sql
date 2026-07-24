-- Table: flow_sys_user_role
-- 用户-角色
CREATE TABLE IF NOT EXISTS `flow_sys_user_role` (
  `user_id` varchar(32) NOT NULL,
  `role_id` varchar(32) NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_flow_sys_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';
