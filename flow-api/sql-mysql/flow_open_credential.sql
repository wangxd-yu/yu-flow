-- Table: flow_open_credential
-- 开放平台凭证
CREATE TABLE IF NOT EXISTS `flow_open_credential` (
  `id` varchar(32) NOT NULL,
  `platform_id` varchar(32) NOT NULL,
  `app_key` varchar(64) NOT NULL,
  `app_secret_enc` varchar(512) NOT NULL COMMENT 'AES加密后的secret',
  `secret_hint` varchar(16) COMMENT '末4位提示',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0停用 1启用 2已轮换废弃',
  `rotated_from_id` varchar(32) COMMENT '轮换来源凭证',
  `expire_at` datetime,
  `create_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_open_credential_platform_id` (`platform_id`),
  UNIQUE KEY `uk_flow_open_credential_app_key` (`app_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台凭证';
