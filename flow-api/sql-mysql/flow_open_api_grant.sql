-- Table: flow_open_api_grant
-- 开放平台接口授权
CREATE TABLE IF NOT EXISTS `flow_open_api_grant` (
  `id` varchar(32) NOT NULL,
  `platform_id` varchar(32) NOT NULL,
  `api_id` varchar(32) NOT NULL,
  `allow_methods` varchar(64) COMMENT '空=跟随接口方法',
  `create_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_open_api_grant_api_id` (`api_id`),
  UNIQUE KEY `uk_flow_open_api_grant_platform_id_api_id` (`platform_id`, `api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台接口授权';
