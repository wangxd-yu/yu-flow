-- Table: flow_open_platform
-- 第三方开放平台
CREATE TABLE IF NOT EXISTS `flow_open_platform` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '唯一编码',
  `name` varchar(128) NOT NULL COMMENT '平台名称',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  `contact` varchar(128) COMMENT '联系人',
  `remark` varchar(512) COMMENT '备注',
  `ip_allowlist` varchar(1024) COMMENT 'IP白名单JSON数组，空=不限',
  `expire_at` datetime COMMENT '平台到期时间',
  `open_call_log_enabled` tinyint DEFAULT 1 COMMENT '是否记录入站摘要日志 0关1开',
  `rate_limit_qps` int COMMENT '平台级 QPS 上限，空=不限',
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_open_platform_status` (`status`),
  UNIQUE KEY `uk_flow_open_platform_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方开放平台';
