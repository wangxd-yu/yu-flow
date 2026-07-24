-- 逻辑环境 + 回归套件（发布门禁）
CREATE TABLE IF NOT EXISTS `flow_env` (
    `id`                   VARCHAR(64)  NOT NULL COMMENT '主键',
    `code`                 VARCHAR(32)  NOT NULL COMMENT 'DEV|STAGING|PROD',
    `name`                 VARCHAR(100) NOT NULL COMMENT '显示名',
    `require_suite_pass`   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=发布前需回归通过',
    `pass_ttl_hours`       INT          NOT NULL DEFAULT 24 COMMENT '通过结果有效小时数',
    `enabled`              TINYINT      NOT NULL DEFAULT 1,
    `sort_order`           INT          NOT NULL DEFAULT 0,
    `remark`               VARCHAR(500) NULL,
    `create_time`          DATETIME     NULL,
    `update_time`          DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_env_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布逻辑环境';

CREATE TABLE IF NOT EXISTS `flow_regression_suite` (
    `id`            VARCHAR(64)  NOT NULL,
    `name`          VARCHAR(100) NOT NULL,
    `asset_type`    VARCHAR(32)  NOT NULL COMMENT 'API|TASK|SERVICE',
    `asset_id`      VARCHAR(64)  NOT NULL,
    `enabled`       TINYINT      NOT NULL DEFAULT 1,
    `create_time`   DATETIME     NULL,
    `update_time`   DATETIME     NULL,
    PRIMARY KEY (`id`),
    KEY `idx_reg_suite_asset` (`asset_type`, `asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归测试套件';

CREATE TABLE IF NOT EXISTS `flow_regression_case` (
    `id`                 VARCHAR(64)  NOT NULL,
    `suite_id`           VARCHAR(64)  NOT NULL,
    `name`               VARCHAR(100) NOT NULL,
    `sort_order`         INT          NOT NULL DEFAULT 0,
    `enabled`            TINYINT      NOT NULL DEFAULT 1,
    `headers_json`       TEXT         NULL COMMENT '请求头 JSON（禁止敏感头）',
    `query_json`         TEXT         NULL COMMENT 'Query JSON',
    `body`               MEDIUMTEXT   NULL COMMENT '请求体，≤32KB',
    `expect_trace_status` VARCHAR(20) NULL COMMENT 'success|error，空则不校验',
    `expect_json_path`   VARCHAR(128) NULL COMMENT '简单 JSONPath',
    `expect_value`       VARCHAR(500) NULL COMMENT '期望值（字符串比较）',
    `timeout_ms`         INT          NOT NULL DEFAULT 10000,
    `create_time`        DATETIME     NULL,
    `update_time`        DATETIME     NULL,
    PRIMARY KEY (`id`),
    KEY `idx_reg_case_suite` (`suite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归用例';

CREATE TABLE IF NOT EXISTS `flow_regression_run` (
    `id`            VARCHAR(64)  NOT NULL,
    `suite_id`      VARCHAR(64)  NOT NULL,
    `asset_type`    VARCHAR(32)  NOT NULL,
    `asset_id`      VARCHAR(64)  NOT NULL,
    `env_code`      VARCHAR(32)  NOT NULL,
    `status`        VARCHAR(20)  NOT NULL COMMENT 'RUNNING|PASSED|FAILED|ERROR',
    `total_cases`   INT          NOT NULL DEFAULT 0,
    `passed_cases`  INT          NOT NULL DEFAULT 0,
    `failed_cases`  INT          NOT NULL DEFAULT 0,
    `started_at`    DATETIME     NOT NULL,
    `finished_at`   DATETIME     NULL,
    `triggered_by`  VARCHAR(100) NULL,
    `summary`       VARCHAR(500) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_reg_run_asset_env` (`asset_type`, `asset_id`, `env_code`, `finished_at`),
    KEY `idx_reg_run_suite` (`suite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归运行记录';

CREATE TABLE IF NOT EXISTS `flow_regression_run_case` (
    `id`            VARCHAR(64)  NOT NULL,
    `run_id`        VARCHAR(64)  NOT NULL,
    `case_id`       VARCHAR(64)  NOT NULL,
    `case_name`     VARCHAR(100) NULL,
    `status`        VARCHAR(20)  NOT NULL COMMENT 'PASSED|FAILED|ERROR|SKIPPED',
    `duration_ms`   BIGINT       NULL,
    `message`       VARCHAR(500) NULL,
    `detail_json`   VARCHAR(2000) NULL COMMENT '截断后的摘要，不含全量响应',
    PRIMARY KEY (`id`),
    KEY `idx_reg_run_case_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归用例运行明细';

INSERT INTO `flow_env` (`id`, `code`, `name`, `require_suite_pass`, `pass_ttl_hours`, `enabled`, `sort_order`, `remark`, `create_time`, `update_time`)
VALUES
    ('env_dev', 'DEV', '开发', 0, 72, 1, 10, '默认发布环境，不强制回归', NOW(), NOW()),
    ('env_staging', 'STAGING', '预发', 1, 48, 1, 20, '发布前需回归通过', NOW(), NOW()),
    ('env_prod', 'PROD', '生产', 1, 24, 1, 30, '发布前需回归通过（24h 内）', NOW(), NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
    ('p_release_v', 'flow:release:view', '发布门禁查看', 'ops', NULL, NOW()),
    ('p_release_w', 'flow:release:edit', '发布门禁管理', 'ops', NULL, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
    ('role_operator', 'flow:release:view'),
    ('role_operator', 'flow:release:edit'),
    ('role_viewer', 'flow:release:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);
