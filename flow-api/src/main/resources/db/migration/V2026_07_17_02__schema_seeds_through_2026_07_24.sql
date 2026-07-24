-- Squashed migrations: V2026_07_17_02 .. V2026_07_24_04 (fresh-install / one-shot commit)
-- If flyway_schema_history already recorded the old filenames, do NOT apply this on that DB; use repair/baseline instead.

-- =====================================================================
-- from: V2026_07_17_02__create_flow_log_third.sql
-- =====================================================================
CREATE TABLE IF NOT EXISTS `flow_log_third` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `api_type` varchar(50) DEFAULT NULL COMMENT '接口标识',
  `source` varchar(16) DEFAULT NULL COMMENT '调用来源：API/TASK/DEBUG/OTHER',
  `source_ref` varchar(64) DEFAULT NULL COMMENT '来源关联ID（apiId/taskId）',
  `source_name` varchar(128) DEFAULT NULL COMMENT '来源名称（接口名/任务名）',
  `request_url` varchar(512) DEFAULT NULL COMMENT '请求URL',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方法',
  `request_params` text COMMENT '请求参数',
  `request_headers` text COMMENT '请求头',
  `response_status` int DEFAULT NULL COMMENT '响应状态码',
  `response_body` text COMMENT '响应内容',
  `elapsed_time` bigint DEFAULT NULL COMMENT '请求耗时(ms)',
  `is_success` tinyint DEFAULT NULL COMMENT '是否成功（0失败/1成功）',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `curl` text COMMENT 'Curl命令',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_source` (`source`),
  KEY `idx_api_type` (`api_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方接口调用日志';


-- =====================================================================
-- from: V2026_07_17_03__add_flow_log_third_source_name.sql
-- =====================================================================
-- 存量库补 source_name；若建表脚本已含该列则跳过
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_log_third'
    AND COLUMN_NAME = 'source_name'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_log_third` ADD COLUMN `source_name` varchar(128) DEFAULT NULL COMMENT ''来源名称（接口名/任务名）'' AFTER `source_ref`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- from: V2026_07_17_04__add_flow_api_cache_config.sql
-- =====================================================================
-- 动态 API 查询响应 Redis 缓存配置（JSON）

ALTER TABLE flow_api_info
    ADD COLUMN cache_config TEXT NULL COMMENT '响应缓存配置 JSON：enabled/ttlSeconds/keyParams/includePageable';


-- =====================================================================
-- from: V2026_07_19_01__migrate_demo_legacy_dsl.sql
-- =====================================================================
-- 将演示 FLOW API 从遗留 start/set/end 迁移为 request/evaluate/response
UPDATE flow_api_info
SET dsl_content = '{
  "startStepId": "req",
  "steps": [
    {
      "id": "req",
      "type": "request",
      "name": "请求入口",
      "next": {
        "params": "eval_transform"
      }
    },
    {
      "id": "eval_transform",
      "type": "evaluate",
      "name": "数据转换",
      "language": "JavaScript",
      "expression": "({ code: 200, message: ''数据转换成功'', data: { requested: true, ts: Date.now() } })",
      "next": {
        "out": "resp"
      }
    },
    {
      "id": "resp",
      "type": "response",
      "name": "响应输出",
      "status": 200,
      "body": "${eval_transform}"
    }
  ]
}',
    info = '利用 Evaluate 节点对入参进行转换并组装返回结果。',
    update_time = NOW()
WHERE id = 'demo_api_flow_1';


-- =====================================================================
-- from: V2026_07_20__add_flow_service_info.sql
-- =====================================================================
-- 内部服务编排定义表（Service Flow）
CREATE TABLE IF NOT EXISTS `flow_service_info` (
    `id`            VARCHAR(32)   NOT NULL COMMENT '雪花ID',
    `name`          VARCHAR(128)  NOT NULL COMMENT '服务名称',
    `directory_id`  VARCHAR(32)            COMMENT '关联目录ID（复用全局目录树）',
    `enabled`       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
    `log_enabled`   TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
    `dsl_content`   MEDIUMTEXT             COMMENT '流程定义 DSL JSON（草稿）',
    `info`          VARCHAR(512)           COMMENT '服务描述',
    `tags`          VARCHAR(255)           COMMENT '标签，英文逗号分隔',
    `deleted`       INT           NOT NULL DEFAULT 0 COMMENT '软删除：0=正常, 1=已删除',
    `create_time`   DATETIME               COMMENT '创建时间',
    `update_time`   DATETIME               COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_info_directory_id` (`directory_id`),
    KEY `idx_service_info_enabled` (`enabled`),
    KEY `idx_service_info_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部服务编排定义';


-- =====================================================================
-- from: V2026_07_20_02__add_flow_log_service.sql
-- =====================================================================
-- 内部服务编排执行日志表
CREATE TABLE IF NOT EXISTS `flow_log_service` (
    `id`            VARCHAR(32)    NOT NULL COMMENT '雪花ID',
    `service_id`    VARCHAR(32)    NOT NULL COMMENT '关联服务ID',
    `service_name`  VARCHAR(128)            COMMENT '服务名称（冗余）',
    `trigger_type`  VARCHAR(16)    NOT NULL DEFAULT 'MANUAL' COMMENT '触发类型：MANUAL=手动, CALL=流程内调用, DEBUG=调试',
    `status`        VARCHAR(16)    NOT NULL COMMENT '执行状态：SUCCESS / FAILED / RUNNING',
    `cost_time_ms`  BIGINT                  COMMENT '耗时（毫秒）',
    `error_msg`     TEXT                    COMMENT '失败信息',
    `trace_data`    LONGTEXT                COMMENT 'FlowTrace JSON 快照（logEnabled=true 时记录）',
    `create_time`   DATETIME                COMMENT '执行开始时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_service_service_id` (`service_id`),
    KEY `idx_log_service_status` (`status`),
    KEY `idx_log_service_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部服务编排执行日志';


-- =====================================================================
-- from: V2026_07_20_03__add_flow_service_info_contract.sql
-- =====================================================================
-- 内部服务编排：入参/返回契约（JSON，SchemaNode 风格）
ALTER TABLE `flow_service_info`
    ADD COLUMN `contract` MEDIUMTEXT NULL COMMENT '服务契约 JSON：inputs/outputs/outputDescription' AFTER `dsl_content`;


-- =====================================================================
-- from: V2026_07_20_04__add_flow_service_info_publish.sql
-- =====================================================================
-- 内部服务编排：发布状态与快照（CALL 走已发布，调试/手动可用草稿）
ALTER TABLE `flow_service_info`
    ADD COLUMN `publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发布状态：0=未发布, 1=已发布' AFTER `contract`,
    ADD COLUMN `published_snapshot` MEDIUMTEXT NULL COMMENT '发布快照 JSON：dslContent/contract' AFTER `publish_status`,
    ADD COLUMN `publish_time` DATETIME NULL COMMENT '最近发布时间' AFTER `published_snapshot`;

CREATE INDEX `idx_service_info_publish_status` ON `flow_service_info` (`publish_status`);


-- =====================================================================
-- from: V2026_07_20_05__add_flow_directory_biz_type.sql
-- =====================================================================
-- 目录业务域分域：api / task / service / model / page；空=各模块共用（兼容旧数据）
ALTER TABLE `flow_directory`
    ADD COLUMN `biz_type` VARCHAR(32) NULL COMMENT '业务域：api/task/service/model/page，空=共用' AFTER `name`;

CREATE INDEX `idx_directory_biz_type` ON `flow_directory` (`biz_type`);


-- =====================================================================
-- from: V2026_07_20_06__add_flow_asset_version.sql
-- =====================================================================
-- 资产发布历史版本（接口 / 任务 / 服务编排共用）
CREATE TABLE IF NOT EXISTS `flow_asset_version` (
    `id`            VARCHAR(32)   NOT NULL COMMENT '雪花ID',
    `biz_type`      VARCHAR(16)   NOT NULL COMMENT '资产类型：api / task / service',
    `asset_id`      VARCHAR(32)   NOT NULL COMMENT '资产ID',
    `version_no`    INT           NOT NULL COMMENT '同资产内递增版本号',
    `snapshot`      MEDIUMTEXT    NOT NULL COMMENT '发布快照 JSON（与各模块 published_snapshot 同构）',
    `source`        VARCHAR(16)   NOT NULL DEFAULT 'publish' COMMENT '来源：publish / rollback',
    `remark`        VARCHAR(255)           COMMENT '备注',
    `publisher`     VARCHAR(64)            COMMENT '发布人',
    `publish_time`  DATETIME      NOT NULL COMMENT '发布时间',
    `create_time`   DATETIME               COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_asset_ver` (`biz_type`, `asset_id`, `version_no`),
    KEY `idx_biz_asset_time` (`biz_type`, `asset_id`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产发布历史版本';


-- =====================================================================
-- from: V2026_07_20_07__add_flow_task_info_publish.sql
-- =====================================================================
-- 定时任务发布快照（对齐接口 / 服务编排）
ALTER TABLE `flow_task_info`
    ADD COLUMN `publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发布状态：0=未发布，1=已发布' AFTER `dsl_content`,
    ADD COLUMN `published_snapshot` MEDIUMTEXT NULL COMMENT '发布快照 JSON：dslContent' AFTER `publish_status`,
    ADD COLUMN `publish_time` DATETIME NULL COMMENT '最近发布时间' AFTER `published_snapshot`;

-- 已有任务：有 DSL 的视为已发布，避免升级后调度中断
UPDATE `flow_task_info`
SET `publish_status` = 1,
    `published_snapshot` = JSON_OBJECT('dslContent', `dsl_content`),
    `publish_time` = COALESCE(`update_time`, `create_time`, NOW())
WHERE (`deleted` = 0 OR `deleted` IS NULL)
  AND `dsl_content` IS NOT NULL
  AND TRIM(`dsl_content`) <> '';

CREATE INDEX `idx_task_publish_status` ON `flow_task_info` (`publish_status`);


-- =====================================================================
-- from: V2026_07_20_08__seed_asset_version_retention_config.sql
-- =====================================================================
-- 资产历史版本保留数量（系统配置，可在「系统配置」页修改）
-- id 为 bigint 自增，不显式写入；幂等依赖 uk_config_key
INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('ASSET_VERSION_RETENTION_COUNT', '20', 'NUMBER', 'FLOW',
     '接口/任务/服务编排历史版本保留条数（每个资产最多保留最近 N 条，超出自动删除最旧版本；最小为 1）', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE config_key = config_key;


-- =====================================================================
-- from: V2026_07_21__add_flow_metrics_minute.sql
-- =====================================================================
-- 资产运行计量：分钟级汇总（温层）
-- 索引名带表前缀，便于迁移 PostgreSQL（schema 内索引名全局唯一）
CREATE TABLE IF NOT EXISTS `flow_metrics_minute` (
    `id`              VARCHAR(32)    NOT NULL COMMENT '雪花ID',
    `asset_type`      VARCHAR(16)    NOT NULL COMMENT 'API / TASK / SERVICE',
    `asset_id`        VARCHAR(32)    NOT NULL COMMENT '资产ID',
    `trigger_type`    VARCHAR(16)    NOT NULL DEFAULT '_' COMMENT '触发类型；API 固定为 _',
    `bucket_start`    DATETIME       NOT NULL COMMENT '分钟桶起点（整分）',
    `success_cnt`     BIGINT         NOT NULL DEFAULT 0,
    `fail_cnt`        BIGINT         NOT NULL DEFAULT 0,
    `skipped_cnt`     BIGINT         NOT NULL DEFAULT 0,
    `sum_cost_ms`     BIGINT         NOT NULL DEFAULT 0,
    `latency_count`   BIGINT         NOT NULL DEFAULT 0,
    `hist_json`       VARCHAR(1024)           COMMENT '直方图桶计数 JSON 数组',
    `update_time`     DATETIME                COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_metrics_minute_asset_bucket` (`asset_type`, `asset_id`, `trigger_type`, `bucket_start`),
    KEY `idx_metrics_minute_bucket_start` (`bucket_start`),
    KEY `idx_metrics_minute_type_bucket` (`asset_type`, `bucket_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量分钟汇总';


-- =====================================================================
-- from: V2026_07_22_01__add_flow_open_platform.sql
-- =====================================================================
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


-- =====================================================================
-- from: V2026_07_22_02__add_flow_log_open_call.sql
-- =====================================================================
-- 开放平台入站调用摘要日志
CREATE TABLE IF NOT EXISTS `flow_log_open_call` (
    `id`           VARCHAR(32)  NOT NULL COMMENT '雪花ID',
    `platform_id`  VARCHAR(32)           COMMENT '开放平台ID',
    `app_key`      VARCHAR(64)           COMMENT '调用方 AppKey',
    `api_id`       VARCHAR(32)           COMMENT '命中的 API ID',
    `method`       VARCHAR(16)           COMMENT 'HTTP 方法',
    `path`         VARCHAR(512)          COMMENT '真实发布路径',
    `status`       INT                   COMMENT 'HTTP 状态',
    `cost_ms`      BIGINT                COMMENT '耗时毫秒',
    `error_code`   VARCHAR(64)           COMMENT '业务/鉴权错误码',
    `request_id`   VARCHAR(64)           COMMENT '请求追踪ID',
    `create_time`  DATETIME,
    PRIMARY KEY (`id`),
    KEY `idx_flow_log_open_call_platform_time` (`platform_id`, `create_time`),
    KEY `idx_flow_log_open_call_app_key` (`app_key`),
    KEY `idx_flow_log_open_call_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台入站调用摘要';

-- 平台级调用日志开关（1=开启，0=关闭；空按全局默认）
ALTER TABLE `flow_open_platform`
    ADD COLUMN `open_call_log_enabled` TINYINT NULL DEFAULT 1 COMMENT '是否记录入站摘要日志 0关1开' AFTER `expire_at`;


-- =====================================================================
-- from: V2026_07_22_03__add_metrics_auth_fail.sql
-- =====================================================================
-- 鉴权失败计数（不计入业务失败率分母）
ALTER TABLE `flow_metrics_minute`
    ADD COLUMN `auth_fail_cnt` BIGINT NOT NULL DEFAULT 0 COMMENT '鉴权失败次数（如开放平台 401/403）' AFTER `fail_cnt`;


-- =====================================================================
-- from: V2026_07_22_04__add_open_platform_rate_limit.sql
-- =====================================================================
ALTER TABLE `flow_open_platform`
    ADD COLUMN `rate_limit_qps` INT NULL COMMENT '平台级 QPS 上限，空=不限' AFTER `open_call_log_enabled`;


-- =====================================================================
-- from: V2026_07_22_05__add_flow_metrics_meta.sql
-- =====================================================================
-- 资产运行计量：跨重启持久化的健康元数据（last*/consecFail）
CREATE TABLE IF NOT EXISTS `flow_metrics_meta` (
    `id`               VARCHAR(32)  NOT NULL COMMENT '雪花ID',
    `asset_type`       VARCHAR(16)  NOT NULL COMMENT 'API / TASK / SERVICE / PLATFORM',
    `asset_id`         VARCHAR(32)  NOT NULL COMMENT '资产ID',
    `last_success_at`  BIGINT                COMMENT '最近成功 epoch ms',
    `last_fail_at`     BIGINT                COMMENT '最近业务失败 epoch ms',
    `consec_fail`      BIGINT       NOT NULL DEFAULT 0 COMMENT '连续业务失败次数',
    `update_time`      DATETIME              COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_metrics_meta_asset` (`asset_type`, `asset_id`),
    KEY `idx_metrics_meta_type` (`asset_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量元数据';


-- =====================================================================
-- from: V2026_07_22_06__add_api_security_config.sql
-- =====================================================================
-- 已发布 API 入站防护（按接口覆盖全局 yu.flow.ingress）
ALTER TABLE `flow_api_info`
    ADD COLUMN `security_config` TEXT NULL COMMENT '入站防护 JSON：authMode/antiReplay/rateLimit/ipAllowlist' AFTER `cache_config`;


-- =====================================================================
-- from: V2026_07_22_07__seed_open_ingress_sys_config.sql
-- =====================================================================
-- 开放平台 / 入站防护：系统配置（可热更新）
-- 读取优先级：flow_sys_config（启用） > application.yml / 环境变量 > 代码默认
-- 停用(status=0)或删除后自动回退 yml，避免初始化无库配置时不可用
INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('OPEN_ENABLED', 'true', 'BOOLEAN', 'OPEN',
     '开放入口总开关（/flow-api/open/**）。停用本项后回退 yu.flow.open.enabled', 1, 1, NOW(), NOW()),
    ('OPEN_ALLOW_PLAIN_SECRET', 'false', 'BOOLEAN', 'OPEN',
     '是否允许 X-Yu-App-Secret 明文头（生产务必 false）', 1, 1, NOW(), NOW()),
    ('OPEN_ALLOW_DIRECT_PATH', 'false', 'BOOLEAN', 'OPEN',
     '是否允许 AppKey 直打真实发布 path（默认仅开放前缀入口）', 1, 1, NOW(), NOW()),
    ('OPEN_REQUIRE_HOST_AUTH', 'false', 'BOOLEAN', 'OPEN',
     'ingress 关闭时：无 AppKey 访问已发布 API 是否强制宿主登录', 1, 1, NOW(), NOW()),
    ('OPEN_CALL_LOG_ENABLED', 'true', 'BOOLEAN', 'OPEN',
     '开放入站摘要日志全局开关（平台可单独关闭）', 1, 1, NOW(), NOW()),
    ('OPEN_SKEW_SECONDS', '300', 'NUMBER', 'OPEN',
     'HMAC 签名时钟偏差（秒）', 1, 1, NOW(), NOW()),
    ('OPEN_NONCE_FAIL_CLOSED', 'true', 'BOOLEAN', 'OPEN',
     'nonce 写入 Redis 失败时是否拒绝请求（生产建议 true）', 1, 1, NOW(), NOW()),
    ('OPEN_INCLUDE_BODY_HASH', 'true', 'BOOLEAN', 'OPEN',
     'HMAC 是否纳入 body SHA-256', 1, 1, NOW(), NOW()),
    ('OPEN_ROTATE_GRACE_HOURS', '24', 'NUMBER', 'OPEN',
     '密钥轮换后旧密可用宽限期（小时）；≤0 立即失效', 1, 1, NOW(), NOW()),

    ('INGRESS_ENABLED', 'false', 'BOOLEAN', 'INGRESS',
     '已发布 API 入站防护总开关。false=信任宿主网关。停用本项后回退 yu.flow.ingress.enabled', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_AUTH_MODE', 'NONE', 'STRING', 'INGRESS',
     '默认鉴权：NONE | HOST | OPEN（接口可覆盖）', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_ANTI_REPLAY', 'true', 'BOOLEAN', 'INGRESS',
     '默认防重放（仅 OPEN 鉴权生效）', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_RATE_LIMIT_ENABLED', 'false', 'BOOLEAN', 'INGRESS',
     '默认是否启用接口限流', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_RATE_LIMIT_QPS', '100', 'NUMBER', 'INGRESS',
     '默认限流 QPS（秒级固定窗口）', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_IP_ALLOWLIST', '', 'STRING', 'INGRESS',
     '默认 IP 白名单（空=不限制；逗号分隔 IP/CIDR）', 1, 1, NOW(), NOW()),
    ('INGRESS_RATE_LIMIT_FAIL_OPEN', 'true', 'BOOLEAN', 'INGRESS',
     '限流 Redis 失败时是否放行（fail-open）', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE config_key = config_key;


-- =====================================================================
-- from: V2026_07_23_01__add_sys_rbac.sql
-- =====================================================================
-- RBAC：用户 / 角色 / 权限
CREATE TABLE IF NOT EXISTS `flow_sys_user` (
    `id`            VARCHAR(32)  NOT NULL,
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码',
    `display_name`  VARCHAR(64)  NULL COMMENT '显示名',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `is_builtin`    TINYINT      NOT NULL DEFAULT 0 COMMENT '1内置不可删',
    `remark`        VARCHAR(255) NULL,
    `create_time`   DATETIME     NULL,
    `update_time`   DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS `flow_sys_role` (
    `id`          VARCHAR(32)  NOT NULL,
    `role_code`   VARCHAR(64)  NOT NULL COMMENT 'ADMIN/OPERATOR/VIEWER',
    `role_name`   VARCHAR(64)  NOT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `is_builtin`  TINYINT      NOT NULL DEFAULT 0,
    `remark`      VARCHAR(255) NULL,
    `create_time` DATETIME     NULL,
    `update_time` DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS `flow_sys_permission` (
    `id`          VARCHAR(32)  NOT NULL,
    `perm_code`   VARCHAR(128) NOT NULL COMMENT '如 flow:api:write',
    `perm_name`   VARCHAR(64)  NOT NULL,
    `group_code`  VARCHAR(64)  NULL COMMENT '分组：flow/ops/infra/sys',
    `remark`      VARCHAR(255) NULL,
    `create_time` DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';

CREATE TABLE IF NOT EXISTS `flow_sys_user_role` (
    `user_id` VARCHAR(32) NOT NULL,
    `role_id` VARCHAR(32) NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

CREATE TABLE IF NOT EXISTS `flow_sys_role_permission` (
    `role_id`   VARCHAR(32)  NOT NULL,
    `perm_code` VARCHAR(128) NOT NULL,
    PRIMARY KEY (`role_id`, `perm_code`),
    KEY `idx_role_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';

-- 内置角色
INSERT INTO `flow_sys_role` (`id`, `role_code`, `role_name`, `status`, `is_builtin`, `remark`, `create_time`, `update_time`)
VALUES
    ('role_admin', 'ADMIN', '管理员', 1, 1, '全部权限', NOW(), NOW()),
    ('role_operator', 'OPERATOR', '运维员', 1, 1, '编排与观测，无用户/系统配置写', NOW(), NOW()),
    ('role_viewer', 'VIEWER', '只读员', 1, 1, '只读查看', NOW(), NOW())
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- 权限点（菜单级）
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
    ('p_home', 'home:view', '首页', 'home', NULL, NOW()),
    ('p_api_v', 'flow:api:view', '接口查看', 'flow', NULL, NOW()),
    ('p_api_w', 'flow:api:write', '接口编排', 'flow', NULL, NOW()),
    ('p_task_v', 'flow:task:view', '任务查看', 'flow', NULL, NOW()),
    ('p_task_w', 'flow:task:write', '任务编排', 'flow', NULL, NOW()),
    ('p_svc_v', 'flow:service:view', '服务查看', 'flow', NULL, NOW()),
    ('p_svc_w', 'flow:service:write', '服务编排', 'flow', NULL, NOW()),
    ('p_page_v', 'flow:page:view', '页面查看', 'flow', NULL, NOW()),
    ('p_page_w', 'flow:page:write', '页面设计', 'flow', NULL, NOW()),
    ('p_rt_v', 'flow:runtime:view', '运行中心', 'ops', NULL, NOW()),
    ('p_open_v', 'flow:open:view', '开放平台查看', 'ops', NULL, NOW()),
    ('p_open_w', 'flow:open:write', '开放平台管理', 'ops', NULL, NOW()),
    ('p_log_v', 'log:view', '日志中心', 'ops', NULL, NOW()),
    ('p_ds_v', 'flow:ds:view', '数据源查看', 'infra', NULL, NOW()),
    ('p_ds_w', 'flow:ds:write', '数据源管理', 'infra', NULL, NOW()),
    ('p_model_v', 'flow:model:view', '模型查看', 'infra', NULL, NOW()),
    ('p_model_w', 'flow:model:write', '模型管理', 'infra', NULL, NOW()),
    ('p_tpl_v', 'sys:template:view', '响应模板查看', 'sys', NULL, NOW()),
    ('p_tpl_w', 'sys:template:write', '响应模板管理', 'sys', NULL, NOW()),
    ('p_macro_v', 'sys:macro:view', '全局参数查看', 'sys', NULL, NOW()),
    ('p_macro_w', 'sys:macro:write', '全局参数管理', 'sys', NULL, NOW()),
    ('p_cfg_v', 'sys:config:view', '系统配置查看', 'sys', NULL, NOW()),
    ('p_cfg_w', 'sys:config:write', '系统配置管理', 'sys', NULL, NOW()),
    ('p_user_v', 'sys:user:view', '用户查看', 'sys', NULL, NOW()),
    ('p_user_w', 'sys:user:write', '用户管理', 'sys', NULL, NOW()),
    ('p_docs', 'docs:view', '接口文档', 'sys', NULL, NOW()),
    ('p_all', '*', '全部权限', 'sys', 'ADMIN 超权', NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- ADMIN → *
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES ('role_admin', '*')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);

-- OPERATOR：编排写 + 观测 + 基础设施写，无用户/系统配置写
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
    ('role_operator', 'home:view'),
    ('role_operator', 'flow:api:view'),
    ('role_operator', 'flow:api:write'),
    ('role_operator', 'flow:task:view'),
    ('role_operator', 'flow:task:write'),
    ('role_operator', 'flow:service:view'),
    ('role_operator', 'flow:service:write'),
    ('role_operator', 'flow:page:view'),
    ('role_operator', 'flow:page:write'),
    ('role_operator', 'flow:runtime:view'),
    ('role_operator', 'flow:open:view'),
    ('role_operator', 'flow:open:write'),
    ('role_operator', 'log:view'),
    ('role_operator', 'flow:ds:view'),
    ('role_operator', 'flow:ds:write'),
    ('role_operator', 'flow:model:view'),
    ('role_operator', 'flow:model:write'),
    ('role_operator', 'sys:template:view'),
    ('role_operator', 'sys:template:write'),
    ('role_operator', 'sys:macro:view'),
    ('role_operator', 'sys:macro:write'),
    ('role_operator', 'sys:config:view'),
    ('role_operator', 'docs:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);

-- VIEWER：只读
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
    ('role_viewer', 'home:view'),
    ('role_viewer', 'flow:api:view'),
    ('role_viewer', 'flow:task:view'),
    ('role_viewer', 'flow:service:view'),
    ('role_viewer', 'flow:page:view'),
    ('role_viewer', 'flow:runtime:view'),
    ('role_viewer', 'flow:open:view'),
    ('role_viewer', 'log:view'),
    ('role_viewer', 'flow:ds:view'),
    ('role_viewer', 'flow:model:view'),
    ('role_viewer', 'sys:template:view'),
    ('role_viewer', 'sys:macro:view'),
    ('role_viewer', 'sys:config:view'),
    ('role_viewer', 'docs:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);

-- RBAC 开关（默认开；停用后回退 yml 单账号）
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `create_time`, `update_time`)
VALUES
    ('RBAC_ENABLED', 'true', 'BOOLEAN', 'SECURITY',
     '是否启用数据库用户 RBAC；false 时登录回退 yu.flow.username/password', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;


-- =====================================================================
-- from: V2026_07_23_02__add_sys_role_perm.sql
-- =====================================================================
-- 角色管理权限点
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
    ('p_role_v', 'sys:role:view', '角色查看', 'sys', NULL, NOW()),
    ('p_role_w', 'sys:role:write', '角色管理', 'sys', NULL, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- ADMIN 已有 *，无需再绑；此处显式绑定便于非 * 自定义管理员角色复用
-- （无操作）


-- =====================================================================
-- from: V2026_07_23_03__seed_api_timeout_sys_config.sql
-- =====================================================================
-- 接口级执行超时：全局默认（毫秒）；≤0 不限制；接口 securityConfig.timeoutMs 可覆盖
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `create_time`, `update_time`)
VALUES
    ('INGRESS_DEFAULT_TIMEOUT_MS', '30000', 'NUMBER', 'INGRESS',
     '已发布 API 默认执行超时（毫秒）。≤0 不限制。接口 securityConfig.timeoutMs 可覆盖，改完需发布', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;


-- =====================================================================
-- from: V2026_07_23_04__seed_alert_sys_config.sql
-- =====================================================================
-- 运行告警（Webhook）：复用 metrics anomalies TopN
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `create_time`, `update_time`)
VALUES
    ('ALERT_ENABLED', 'false', 'BOOLEAN', 'ALERT',
     '运行告警总开关。开启后按间隔扫描近窗口异常并推送 Webhook', 1, 1, NOW(), NOW()),
    ('ALERT_WEBHOOK_URL', '', 'STRING', 'ALERT',
     'Webhook URL（钉钉/企微自定义机器人或任意 HTTP 接收端）', 1, 1, NOW(), NOW()),
    ('ALERT_INTERVAL_MINUTES', '15', 'NUMBER', 'ALERT',
     '扫描间隔（分钟）', 1, 1, NOW(), NOW()),
    ('ALERT_TOP_N', '10', 'NUMBER', 'ALERT',
     '每次最多推送异常条数', 1, 1, NOW(), NOW()),
    ('ALERT_WINDOW', '24h', 'STRING', 'ALERT',
     '指标窗口：1h / 24h / 7d', 1, 1, NOW(), NOW()),
    ('ALERT_MIN_HEALTH', 'error', 'STRING', 'ALERT',
     '最低告警健康度：error 仅严重；warn 含预警', 1, 1, NOW(), NOW()),
    ('ALERT_DEDUP_MINUTES', '60', 'NUMBER', 'ALERT',
     '同一资产告警去重静默（分钟）', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;


-- =====================================================================
-- from: V2026_07_23_05__add_config_audit_log.sql
-- =====================================================================
-- 配置变更审计日志
CREATE TABLE IF NOT EXISTS `flow_log_audit` (
    `id`          VARCHAR(64)  NOT NULL,
    `action`      VARCHAR(64)  NOT NULL COMMENT 'API_PUBLISH|SYS_CONFIG_UPDATE|OPEN_SECRET_ROTATE',
    `operator`    VARCHAR(100) NULL COMMENT '操作人',
    `target_type` VARCHAR(64)  NULL COMMENT 'API|SYS_CONFIG|OPEN_CREDENTIAL',
    `target_id`   VARCHAR(64)  NULL,
    `detail`      VARCHAR(1024) NULL COMMENT 'JSON 摘要，不含密钥',
    `create_time` DATETIME     NULL,
    PRIMARY KEY (`id`),
    KEY `idx_audit_action_time` (`action`, `create_time`),
    KEY `idx_audit_operator` (`operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置变更审计';


-- =====================================================================
-- from: V2026_07_23_06__add_sys_config_sort_order.sql
-- =====================================================================
-- 系统配置组内排序
ALTER TABLE `flow_sys_config`
    ADD COLUMN `sort_order` INT NOT NULL DEFAULT 100 COMMENT '组内展示顺序，越小越靠前' AFTER `status`;

-- 运行告警：开关置顶
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'ALERT_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'ALERT_WEBHOOK_URL';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'ALERT_INTERVAL_MINUTES';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'ALERT_TOP_N';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'ALERT_WINDOW';
UPDATE `flow_sys_config` SET `sort_order` = 60 WHERE `config_key` = 'ALERT_MIN_HEALTH';
UPDATE `flow_sys_config` SET `sort_order` = 70 WHERE `config_key` = 'ALERT_DEDUP_MINUTES';

-- 入站防护：开关置顶
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'INGRESS_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'INGRESS_DEFAULT_AUTH_MODE';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'INGRESS_DEFAULT_ANTI_REPLAY';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'INGRESS_DEFAULT_RATE_LIMIT_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'INGRESS_DEFAULT_RATE_LIMIT_QPS';
UPDATE `flow_sys_config` SET `sort_order` = 60 WHERE `config_key` = 'INGRESS_DEFAULT_IP_ALLOWLIST';
UPDATE `flow_sys_config` SET `sort_order` = 70 WHERE `config_key` = 'INGRESS_RATE_LIMIT_FAIL_OPEN';
UPDATE `flow_sys_config` SET `sort_order` = 80 WHERE `config_key` = 'INGRESS_DEFAULT_TIMEOUT_MS';

-- 开放平台：开关置顶
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'OPEN_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'OPEN_ALLOW_PLAIN_SECRET';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'OPEN_ALLOW_DIRECT_PATH';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'OPEN_REQUIRE_HOST_AUTH';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'OPEN_CALL_LOG_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 60 WHERE `config_key` = 'OPEN_SKEW_SECONDS';
UPDATE `flow_sys_config` SET `sort_order` = 70 WHERE `config_key` = 'OPEN_NONCE_FAIL_CLOSED';
UPDATE `flow_sys_config` SET `sort_order` = 80 WHERE `config_key` = 'OPEN_INCLUDE_BODY_HASH';
UPDATE `flow_sys_config` SET `sort_order` = 90 WHERE `config_key` = 'OPEN_ROTATE_GRACE_HOURS';

-- 安全配置：RBAC 开关置顶，其次登录/Token
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'RBAC_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'LOGIN_MAX_RETRY';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'LOGIN_LOCK_DURATION';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'TOKEN_EXPIRE';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'TOKEN_REFRESH_EXPIRE';


-- =====================================================================
-- from: V2026_07_23_07__seed_security_sys_config_sort.sql
-- =====================================================================
-- 安全配置组内顺序（开关置顶；若 V2026_07_23_06 已执行过则补齐）
UPDATE `flow_sys_config` SET `sort_order` = 10 WHERE `config_key` = 'RBAC_ENABLED';
UPDATE `flow_sys_config` SET `sort_order` = 20 WHERE `config_key` = 'LOGIN_MAX_RETRY';
UPDATE `flow_sys_config` SET `sort_order` = 30 WHERE `config_key` = 'LOGIN_LOCK_DURATION';
UPDATE `flow_sys_config` SET `sort_order` = 40 WHERE `config_key` = 'TOKEN_EXPIRE';
UPDATE `flow_sys_config` SET `sort_order` = 50 WHERE `config_key` = 'TOKEN_REFRESH_EXPIRE';


-- =====================================================================
-- from: V2026_07_23_08__add_alert_product.sql
-- =====================================================================
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


-- =====================================================================
-- from: V2026_07_23_09__backfill_directory_biz_type.sql
-- =====================================================================
-- 按资产引用回填目录 biz_type（仅被单一域引用才打标；多域/无引用保持空=共享）
UPDATE `flow_directory` d
SET `biz_type` = 'api'
WHERE (d.`biz_type` IS NULL OR d.`biz_type` = '')
  AND EXISTS (SELECT 1 FROM `flow_api_info` a WHERE a.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_task_info` t WHERE t.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_service_info` s WHERE s.`directory_id` = d.`id`);

UPDATE `flow_directory` d
SET `biz_type` = 'task'
WHERE (d.`biz_type` IS NULL OR d.`biz_type` = '')
  AND EXISTS (SELECT 1 FROM `flow_task_info` t WHERE t.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_api_info` a WHERE a.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_service_info` s WHERE s.`directory_id` = d.`id`);

UPDATE `flow_directory` d
SET `biz_type` = 'service'
WHERE (d.`biz_type` IS NULL OR d.`biz_type` = '')
  AND EXISTS (SELECT 1 FROM `flow_service_info` s WHERE s.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_api_info` a WHERE a.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_task_info` t WHERE t.`directory_id` = d.`id`);


-- =====================================================================
-- from: V2026_07_23_10__seed_mail_sys_config.sql
-- =====================================================================
-- SMTP 邮件全局配置（告警 / 后续流程编排共用；优先于 yu.flow.mail yml）
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `sort_order`, `create_time`, `update_time`)
VALUES
    ('MAIL_ENABLED', 'false', 'BOOLEAN', 'MAIL',
     '邮件发送总开关。关闭后告警 Email 通道与后续邮件节点均不可发信', 1, 1, 10, NOW(), NOW()),
    ('MAIL_HOST', '', 'STRING', 'MAIL',
     'SMTP 主机，如 smtp.qq.com / smtp.163.com', 1, 1, 20, NOW(), NOW()),
    ('MAIL_PORT', '465', 'NUMBER', 'MAIL',
     'SMTP 端口：SSL 常用 465，STARTTLS 常用 587', 1, 1, 30, NOW(), NOW()),
    ('MAIL_USERNAME', '', 'STRING', 'MAIL',
     'SMTP 登录账号（通常为邮箱地址）', 1, 1, 40, NOW(), NOW()),
    ('MAIL_PASSWORD', '', 'STRING', 'MAIL',
     'SMTP 密码或应用专用密码', 1, 1, 50, NOW(), NOW()),
    ('MAIL_FROM', '', 'STRING', 'MAIL',
     '发件人地址；为空则使用 MAIL_USERNAME', 1, 1, 60, NOW(), NOW()),
    ('MAIL_SSL', 'true', 'BOOLEAN', 'MAIL',
     '启用 SMTPS/SSL（465）', 1, 1, 70, NOW(), NOW()),
    ('MAIL_STARTTLS', 'false', 'BOOLEAN', 'MAIL',
     '启用 STARTTLS（587）；与 SSL 二选一为主', 1, 1, 80, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `remark` = VALUES(`remark`),
    `sort_order` = VALUES(`sort_order`);


-- =====================================================================
-- from: V2026_07_23_11__seed_log_retention_config.sql
-- =====================================================================
-- 日志定时清理保留天数（LOG 分组；0 = 不清理）
-- 补齐 Task / Service / Third / Open / Audit / Alert；并为既有键写入 sort_order

INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `sort_order`, `create_time`, `update_time`)
VALUES
    ('LOG_EXECUTION_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     'API 执行日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 10, NOW(), NOW()),
    ('LOG_LOGIN_RETENTION_DAYS', '90', 'NUMBER', 'LOG',
     '登录审计日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 20, NOW(), NOW()),
    ('LOG_TASK_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '定时任务执行日志保留天数（flow_log_task；0 = 不清理）', 1, 1, 30, NOW(), NOW()),
    ('LOG_SERVICE_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '服务编排执行日志保留天数（flow_log_service；0 = 不清理）', 1, 1, 40, NOW(), NOW()),
    ('LOG_THIRD_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '第三方调用日志保留天数（flow_log_third；0 = 不清理）', 1, 1, 50, NOW(), NOW()),
    ('LOG_OPEN_CALL_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '开放平台调用日志保留天数（flow_log_open_call；0 = 不清理）', 1, 1, 60, NOW(), NOW()),
    ('LOG_AUDIT_RETENTION_DAYS', '180', 'NUMBER', 'LOG',
     '配置变更审计日志保留天数（flow_log_audit；0 = 不清理）', 1, 1, 70, NOW(), NOW()),
    ('LOG_ALERT_EVENT_RETENTION_DAYS', '90', 'NUMBER', 'LOG',
     '告警历史事件保留天数（flow_alert_event；0 = 不清理）', 1, 1, 80, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `remark` = VALUES(`remark`),
    `sort_order` = VALUES(`sort_order`);


-- =====================================================================
-- from: V2026_07_23_12__datasource_wall_config.sql
-- =====================================================================
-- 数据源 SQL 安全墙（Druid Wall）配置 + 默认数据源哨兵行

ALTER TABLE `flow_datasource`
    ADD COLUMN `wall_config` TEXT NULL COMMENT 'SQL安全墙JSON(DataSourceWallConfig)' AFTER `status`,
    ADD COLUMN `is_system` TINYINT NOT NULL DEFAULT 0 COMMENT '系统数据源(1=不可删改连接，如[DEFAULT])' AFTER `wall_config`;

-- 系统默认数据源哨兵行：连接池仍走 spring.datasource；仅 wall_config 可编辑
INSERT INTO `flow_datasource` (
    `id`, `name`, `code`, `db_type`, `driver_class_name`, `url`, `username`, `password`,
    `initial_size`, `min_idle`, `max_active`, `status`, `wall_config`, `is_system`,
    `health_status`, `error_count`, `create_time`, `update_time`
) VALUES (
    'ds_system_default',
    '系统默认数据源',
    '[DEFAULT]',
    'mysql',
    'com.mysql.cj.jdbc.Driver',
    '',
    '',
    '',
    5, 5, 20, 1,
    '{"enabled":true,"multiStatementAllow":false,"commentAllow":false,"noneBaseStatementAllow":false,"selectAllow":true,"insertAllow":true,"updateAllow":true,"deleteAllow":true,"tableCheck":true,"tableWhiteList":[],"tableBlackList":[],"functionBlackList":["sleep","benchmark","load_file","updatexml","extractvalue","pg_sleep"],"variantCheck":true}',
    1,
    'UNKNOWN',
    0,
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE
    `is_system` = 1,
    `name` = VALUES(`name`);


-- =====================================================================
-- from: V2026_07_23_13__harden_ingress_auth_defaults.sql
-- =====================================================================
-- 安全加固：入站防护默认开启 + HOST 鉴权；开放入口默认要求宿主登录
UPDATE `flow_sys_config`
SET `config_value` = 'true',
    `remark`      = '已发布 API 入站防护总开关（安全默认开启；false=关闭后网关仍强制管理端 JWT）',
    `update_time` = NOW()
WHERE `config_key` = 'INGRESS_ENABLED';

UPDATE `flow_sys_config`
SET `config_value` = 'HOST',
    `remark`      = '默认鉴权模式：NONE|HOST|OPEN（安全默认 HOST，需管理端 JWT）',
    `update_time` = NOW()
WHERE `config_key` = 'INGRESS_DEFAULT_AUTH_MODE';

UPDATE `flow_sys_config`
SET `config_value` = 'true',
    `remark`      = '无 AppKey 访问已发布 API 时是否强制宿主登录（JWT）',
    `update_time` = NOW()
WHERE `config_key` = 'OPEN_REQUIRE_HOST_AUTH';


-- =====================================================================
-- from: V2026_07_24_01__add_release_gate.sql
-- =====================================================================
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


-- =====================================================================
-- from: V2026_07_24_02__add_api_view_export_config.sql
-- =====================================================================
-- API 数据查看 / Excel 导出列配置
ALTER TABLE `flow_api_info`
    ADD COLUMN `view_export_config` TEXT NULL COMMENT '数据查看与导出 JSON：columns/sheetName/maxExportRows' AFTER `security_config`;


-- =====================================================================
-- from: V2026_07_24_03__add_api_excel_template.sql
-- =====================================================================
-- API 导出 Excel 公司模板（按接口一份，上传覆盖）
CREATE TABLE IF NOT EXISTS `flow_api_excel_template` (
    `id`            VARCHAR(64)  NOT NULL COMMENT '主键',
    `api_id`        VARCHAR(64)  NOT NULL COMMENT '接口 ID',
    `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `content_type`  VARCHAR(120) NULL COMMENT 'MIME',
    `content`       MEDIUMBLOB   NOT NULL COMMENT 'xlsx 二进制',
    `file_size`     INT          NOT NULL DEFAULT 0,
    `create_time`   DATETIME     NULL,
    `update_time`   DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_excel_tpl_api` (`api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Excel 导出模板';


-- =====================================================================
-- from: V2026_07_24_04__enable_default_datasource_wall.sql
-- =====================================================================
-- [DEFAULT] 系统数据源：历史种子为 enabled=false，与 SQL 执行 fail-closed 冲突；统一改为启用默认安全墙
UPDATE `flow_datasource`
SET `wall_config` = '{"enabled":true,"multiStatementAllow":false,"commentAllow":false,"noneBaseStatementAllow":false,"selectAllow":true,"insertAllow":true,"updateAllow":true,"deleteAllow":true,"tableCheck":true,"tableWhiteList":[],"tableBlackList":[],"functionBlackList":["sleep","benchmark","load_file","updatexml","extractvalue","pg_sleep"],"variantCheck":true}',
    `update_time` = NOW()
WHERE `code` = '[DEFAULT]'
  AND `is_system` = 1;


