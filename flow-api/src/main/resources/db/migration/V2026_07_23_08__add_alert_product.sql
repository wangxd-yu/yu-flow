-- 告警产品化：通道 / 规则 / 历史
CREATE TABLE IF NOT EXISTS `flow_alert_channel` (
    `id`            VARCHAR(64)  NOT NULL COMMENT '主键',
    `name`          VARCHAR(100) NOT NULL COMMENT '通道名称',
    `type`          VARCHAR(20)  NOT NULL COMMENT 'WEBHOOK | EMAIL',
    `config_json`   TEXT         NULL COMMENT '通道配置 JSON',
    `enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `create_time`   DATETIME     NULL,
    `update_time`   DATETIME     NULL,
    PRIMARY KEY (`id`),
    KEY `idx_alert_channel_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警通道';

CREATE TABLE IF NOT EXISTS `flow_alert_rule` (
    `id`                  VARCHAR(64)  NOT NULL COMMENT '主键',
    `name`                VARCHAR(100) NOT NULL COMMENT '规则名称',
    `enabled`             TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `scope_asset_types`   VARCHAR(200) NULL COMMENT '资产类型 CSV：API,TASK,SERVICE,PLATFORM；空=全部',
    `window`              VARCHAR(20)  NOT NULL DEFAULT '24h' COMMENT '1h/24h/7d',
    `min_health`          VARCHAR(20)  NOT NULL DEFAULT 'error' COMMENT 'error|warn',
    `top_n`               INT          NOT NULL DEFAULT 10,
    `channel_ids`         VARCHAR(500) NULL COMMENT '通道 ID JSON 数组',
    `interval_minutes`    INT          NOT NULL DEFAULT 15,
    `dedup_minutes`       INT          NOT NULL DEFAULT 60,
    `create_time`         DATETIME     NULL,
    `update_time`         DATETIME     NULL,
    PRIMARY KEY (`id`),
    KEY `idx_alert_rule_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则';

CREATE TABLE IF NOT EXISTS `flow_alert_event` (
    `id`            VARCHAR(64)  NOT NULL COMMENT '主键',
    `rule_id`       VARCHAR(64)  NULL COMMENT '规则 ID，SysConfig 兜底为空',
    `rule_name`     VARCHAR(100) NULL,
    `fingerprint`   VARCHAR(200) NULL COMMENT '去重指纹',
    `asset_type`    VARCHAR(32)  NULL,
    `asset_id`      VARCHAR(64)  NULL,
    `asset_name`    VARCHAR(200) NULL,
    `health`        VARCHAR(20)  NULL,
    `error_rate`    DOUBLE       NULL,
    `fail_count`    BIGINT       NULL,
    `window`        VARCHAR(20)  NULL,
    `channel_type`  VARCHAR(20)  NULL,
    `channel_id`    VARCHAR(64)  NULL,
    `status`        VARCHAR(20)  NOT NULL COMMENT 'SUCCESS|FAIL|SUPPRESSED',
    `payload_json`  TEXT         NULL,
    `error_msg`     VARCHAR(500) NULL,
    `fired_at`      DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_alert_event_fired` (`fired_at`),
    KEY `idx_alert_event_rule` (`rule_id`),
    KEY `idx_alert_event_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件历史';

-- RBAC
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
    ('p_alert_v', 'flow:alert:view', '告警查看', 'ops', NULL, NOW()),
    ('p_alert_w', 'flow:alert:edit', '告警管理', 'ops', NULL, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
    ('role_operator', 'flow:alert:view'),
    ('role_operator', 'flow:alert:edit'),
    ('role_viewer', 'flow:alert:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);
