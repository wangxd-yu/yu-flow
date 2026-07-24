-- Table: flow_log_login
-- 登录日志表
CREATE TABLE IF NOT EXISTS `flow_log_login` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `account` varchar(100) NOT NULL COMMENT '登录账号',
  `ip` varchar(64) COMMENT '客户端 IP',
  `region` varchar(255) COMMENT 'IP 归属地区',
  `user_agent` varchar(512) COMMENT '浏览器 User-Agent',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '登录状态（1: 成功, 0: 失败）',
  `msg` varchar(255) COMMENT '登录结果信息',
  `duration` bigint COMMENT '登录耗时（毫秒）',
  `create_time` datetime COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_login_account` (`account`),
  KEY `idx_flow_log_login_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';
