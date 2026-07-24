-- 第三方开放平台
CREATE TABLE IF NOT EXISTS `flow_open_platform` (
    `id`            VARCHAR(32)  NOT NULL COMMENT '雪花ID',
    `name`          VARCHAR(128) NOT NULL COMMENT '平台名称',
    `code`          VARCHAR(64)  NOT NULL COMMENT '唯一编码',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    `contact`       VARCHAR(128)          COMMENT '联系人',
    `remark`        VARCHAR(512)          COMMENT '备注',
    `ip_allowlist`  VARCHAR(1024)         COMMENT 'IP白名单JSON数组，空=不限',
    `expire_at`     DATETIME              COMMENT '平台到期时间',
    `create_time`   DATETIME,
    `update_time`   DATETIME,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_open_platform_code` (`code`),
    KEY `idx_flow_open_platform_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方开放平台';

CREATE TABLE IF NOT EXISTS `flow_open_credential` (
    `id`               VARCHAR(32)  NOT NULL,
    `platform_id`      VARCHAR(32)  NOT NULL,
    `app_key`          VARCHAR(64)  NOT NULL,
    `app_secret_enc`   VARCHAR(512) NOT NULL COMMENT 'AES加密后的secret',
    `secret_hint`      VARCHAR(16)           COMMENT '末4位提示',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用 2已轮换废弃',
    `rotated_from_id`  VARCHAR(32)           COMMENT '轮换来源凭证',
    `expire_at`        DATETIME,
    `create_time`      DATETIME,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_open_credential_app_key` (`app_key`),
    KEY `idx_flow_open_credential_platform` (`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台凭证';

CREATE TABLE IF NOT EXISTS `flow_open_api_grant` (
    `id`            VARCHAR(32) NOT NULL,
    `platform_id`   VARCHAR(32) NOT NULL,
    `api_id`        VARCHAR(32) NOT NULL,
    `allow_methods` VARCHAR(64)          COMMENT '空=跟随接口方法',
    `create_time`   DATETIME,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_open_api_grant_platform_api` (`platform_id`, `api_id`),
    KEY `idx_flow_open_api_grant_api` (`api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台接口授权';
