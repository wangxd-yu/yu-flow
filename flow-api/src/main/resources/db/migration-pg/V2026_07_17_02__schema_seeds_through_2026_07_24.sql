-- Squashed migrations (PostgreSQL): V2026_07_17_02 .. V2026_07_24_04
-- Converted from MySQL classpath:db/migration sibling script.
-- Uses ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / ON CONFLICT for idempotency.
-- DEFAULT datasource seed uses postgresql driver placeholders.

-- =====================================================================
-- flow_log_third
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_log_third (
  id              varchar(64)  NOT NULL,
  api_type        varchar(50)  DEFAULT NULL,
  source          varchar(16)  DEFAULT NULL,
  source_ref      varchar(64)  DEFAULT NULL,
  source_name     varchar(128) DEFAULT NULL,
  request_url     varchar(512) DEFAULT NULL,
  request_method  varchar(10)  DEFAULT NULL,
  request_params  text,
  request_headers text,
  response_status integer DEFAULT NULL,
  response_body   text,
  elapsed_time    bigint DEFAULT NULL,
  is_success      smallint DEFAULT NULL,
  error_message   varchar(1000) DEFAULT NULL,
  curl            text,
  create_time     timestamp DEFAULT NULL,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_third IS '第三方接口调用日志';
CREATE INDEX IF NOT EXISTS idx_flow_log_third_create_time ON flow_log_third (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_third_source ON flow_log_third (source);
CREATE INDEX IF NOT EXISTS idx_flow_log_third_api_type ON flow_log_third (api_type);

-- =====================================================================
-- flow_api_info: cache / security / view_export
-- =====================================================================
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS cache_config text;

ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS security_config text;

ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS view_export_config text;

-- =====================================================================
-- demo FLOW DSL migration
-- =====================================================================
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
    update_time = CURRENT_TIMESTAMP
WHERE id = 'demo_api_flow_1';

-- =====================================================================
-- flow_service_info (+ contract / publish)
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_service_info (
    id                 varchar(32)   NOT NULL,
    name               varchar(128)  NOT NULL,
    directory_id       varchar(32),
    enabled            smallint      NOT NULL DEFAULT 1,
    log_enabled        smallint      NOT NULL DEFAULT 1,
    dsl_content        text,
    contract           text,
    publish_status     smallint      NOT NULL DEFAULT 0,
    published_snapshot text,
    publish_time       timestamp,
    info               varchar(512),
    tags               varchar(255),
    deleted            integer       NOT NULL DEFAULT 0,
    create_time        timestamp,
    update_time        timestamp,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_service_info IS '内部服务编排定义';
CREATE INDEX IF NOT EXISTS idx_service_info_directory_id ON flow_service_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_service_info_enabled ON flow_service_info (enabled);
CREATE INDEX IF NOT EXISTS idx_service_info_create_time ON flow_service_info (create_time);
CREATE INDEX IF NOT EXISTS idx_service_info_publish_status ON flow_service_info (publish_status);

ALTER TABLE flow_service_info ADD COLUMN IF NOT EXISTS contract text;
ALTER TABLE flow_service_info ADD COLUMN IF NOT EXISTS publish_status smallint NOT NULL DEFAULT 0;
ALTER TABLE flow_service_info ADD COLUMN IF NOT EXISTS published_snapshot text;
ALTER TABLE flow_service_info ADD COLUMN IF NOT EXISTS publish_time timestamp;

-- =====================================================================
-- flow_log_service
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_log_service (
    id            varchar(32)    NOT NULL,
    service_id    varchar(32)    NOT NULL,
    service_name  varchar(128),
    trigger_type  varchar(16)    NOT NULL DEFAULT 'MANUAL',
    status        varchar(16)    NOT NULL,
    cost_time_ms  bigint,
    error_msg     text,
    trace_data    text,
    create_time   timestamp,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_service IS '内部服务编排执行日志';
CREATE INDEX IF NOT EXISTS idx_log_service_service_id ON flow_log_service (service_id);
CREATE INDEX IF NOT EXISTS idx_log_service_status ON flow_log_service (status);
CREATE INDEX IF NOT EXISTS idx_log_service_create_time ON flow_log_service (create_time);

-- =====================================================================
-- flow_directory.biz_type
-- =====================================================================
ALTER TABLE flow_directory ADD COLUMN IF NOT EXISTS biz_type varchar(32);
CREATE INDEX IF NOT EXISTS idx_directory_biz_type ON flow_directory (biz_type);

-- =====================================================================
-- flow_asset_version
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_asset_version (
    id            varchar(32)   NOT NULL,
    biz_type      varchar(16)   NOT NULL,
    asset_id      varchar(32)   NOT NULL,
    version_no    integer       NOT NULL,
    snapshot      text          NOT NULL,
    source        varchar(16)   NOT NULL DEFAULT 'publish',
    remark        varchar(255),
    publisher     varchar(64),
    publish_time  timestamp     NOT NULL,
    create_time   timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_asset_version_biz_asset_ver UNIQUE (biz_type, asset_id, version_no)
);
COMMENT ON TABLE flow_asset_version IS '资产发布历史版本';
CREATE INDEX IF NOT EXISTS idx_flow_asset_version_biz_asset_time ON flow_asset_version (biz_type, asset_id, publish_time);

-- =====================================================================
-- flow_task_info publish
-- =====================================================================
ALTER TABLE flow_task_info ADD COLUMN IF NOT EXISTS publish_status smallint NOT NULL DEFAULT 0;
ALTER TABLE flow_task_info ADD COLUMN IF NOT EXISTS published_snapshot text;
ALTER TABLE flow_task_info ADD COLUMN IF NOT EXISTS publish_time timestamp;

UPDATE flow_task_info
SET publish_status = 1,
    published_snapshot = jsonb_build_object('dslContent', dsl_content)::text,
    publish_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP)
WHERE (deleted = 0 OR deleted IS NULL)
  AND dsl_content IS NOT NULL
  AND TRIM(dsl_content) <> '';

CREATE INDEX IF NOT EXISTS idx_task_publish_status ON flow_task_info (publish_status);

-- =====================================================================
-- sys config: asset version retention
-- =====================================================================
INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('ASSET_VERSION_RETENTION_COUNT', '20', 'NUMBER', 'FLOW',
     '接口/任务/服务编排历史版本保留条数（每个资产最多保留最近 N 条，超出自动删除最旧版本；最小为 1）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO NOTHING;

-- =====================================================================
-- flow_metrics_minute (+ auth_fail_cnt)
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_metrics_minute (
    id              varchar(32)    NOT NULL,
    asset_type      varchar(16)    NOT NULL,
    asset_id        varchar(32)    NOT NULL,
    trigger_type    varchar(16)    NOT NULL DEFAULT '_',
    bucket_start    timestamp      NOT NULL,
    success_cnt     bigint         NOT NULL DEFAULT 0,
    fail_cnt        bigint         NOT NULL DEFAULT 0,
    auth_fail_cnt   bigint         NOT NULL DEFAULT 0,
    skipped_cnt     bigint         NOT NULL DEFAULT 0,
    sum_cost_ms     bigint         NOT NULL DEFAULT 0,
    latency_count   bigint         NOT NULL DEFAULT 0,
    hist_json       varchar(1024),
    update_time     timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_metrics_minute_asset_bucket UNIQUE (asset_type, asset_id, trigger_type, bucket_start)
);
COMMENT ON TABLE flow_metrics_minute IS '资产运行计量分钟汇总';
CREATE INDEX IF NOT EXISTS idx_metrics_minute_bucket_start ON flow_metrics_minute (bucket_start);
CREATE INDEX IF NOT EXISTS idx_metrics_minute_type_bucket ON flow_metrics_minute (asset_type, bucket_start);
ALTER TABLE flow_metrics_minute ADD COLUMN IF NOT EXISTS auth_fail_cnt bigint NOT NULL DEFAULT 0;

-- =====================================================================
-- open platform
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_open_platform (
    id                    varchar(32)  NOT NULL,
    name                  varchar(128) NOT NULL,
    code                  varchar(64)  NOT NULL,
    status                smallint     NOT NULL DEFAULT 1,
    contact               varchar(128),
    remark                varchar(512),
    ip_allowlist          varchar(1024),
    expire_at             timestamp,
    open_call_log_enabled smallint DEFAULT 1,
    rate_limit_qps        integer,
    create_time           timestamp,
    update_time           timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_open_platform_code UNIQUE (code)
);
COMMENT ON TABLE flow_open_platform IS '第三方开放平台';
CREATE INDEX IF NOT EXISTS idx_flow_open_platform_status ON flow_open_platform (status);
ALTER TABLE flow_open_platform ADD COLUMN IF NOT EXISTS open_call_log_enabled smallint DEFAULT 1;
ALTER TABLE flow_open_platform ADD COLUMN IF NOT EXISTS rate_limit_qps integer;

CREATE TABLE IF NOT EXISTS flow_open_credential (
    id               varchar(32)  NOT NULL,
    platform_id      varchar(32)  NOT NULL,
    app_key          varchar(64)  NOT NULL,
    app_secret_enc   varchar(512) NOT NULL,
    secret_hint      varchar(16),
    status           smallint     NOT NULL DEFAULT 1,
    rotated_from_id  varchar(32),
    expire_at        timestamp,
    create_time      timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_open_credential_app_key UNIQUE (app_key)
);
COMMENT ON TABLE flow_open_credential IS '开放平台凭证';
CREATE INDEX IF NOT EXISTS idx_flow_open_credential_platform ON flow_open_credential (platform_id);

CREATE TABLE IF NOT EXISTS flow_open_api_grant (
    id            varchar(32) NOT NULL,
    platform_id   varchar(32) NOT NULL,
    api_id        varchar(32) NOT NULL,
    allow_methods varchar(64),
    create_time   timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_open_api_grant_platform_api UNIQUE (platform_id, api_id)
);
COMMENT ON TABLE flow_open_api_grant IS '开放平台接口授权';
CREATE INDEX IF NOT EXISTS idx_flow_open_api_grant_api ON flow_open_api_grant (api_id);

CREATE TABLE IF NOT EXISTS flow_log_open_call (
    id           varchar(32)  NOT NULL,
    platform_id  varchar(32),
    app_key      varchar(64),
    api_id       varchar(32),
    method       varchar(16),
    path         varchar(512),
    status       integer,
    cost_ms      bigint,
    error_code   varchar(64),
    request_id   varchar(64),
    create_time  timestamp,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_open_call IS '开放平台入站调用摘要';
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_platform_time ON flow_log_open_call (platform_id, create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_app_key ON flow_log_open_call (app_key);
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_create_time ON flow_log_open_call (create_time);

-- =====================================================================
-- flow_metrics_meta
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_metrics_meta (
    id               varchar(32)  NOT NULL,
    asset_type       varchar(16)  NOT NULL,
    asset_id         varchar(32)  NOT NULL,
    last_success_at  bigint,
    last_fail_at     bigint,
    consec_fail      bigint       NOT NULL DEFAULT 0,
    update_time      timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_metrics_meta_asset UNIQUE (asset_type, asset_id)
);
COMMENT ON TABLE flow_metrics_meta IS '资产运行计量元数据';
CREATE INDEX IF NOT EXISTS idx_metrics_meta_type ON flow_metrics_meta (asset_type);

-- =====================================================================
-- open / ingress sys_config seeds
-- =====================================================================
INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('OPEN_ENABLED', 'true', 'BOOLEAN', 'OPEN',
     '开放入口总开关（/flow-api/open/**）。停用本项后回退 yu.flow.open.enabled', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('OPEN_ALLOW_PLAIN_SECRET', 'false', 'BOOLEAN', 'OPEN',
     '是否允许 X-Yu-App-Secret 明文头（生产务必 false）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('OPEN_ALLOW_DIRECT_PATH', 'false', 'BOOLEAN', 'OPEN',
     '是否允许 AppKey 直打真实发布 path（默认仅开放前缀入口）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('OPEN_REQUIRE_HOST_AUTH', 'false', 'BOOLEAN', 'OPEN',
     'ingress 关闭时：无 AppKey 访问已发布 API 是否强制宿主登录', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('OPEN_CALL_LOG_ENABLED', 'true', 'BOOLEAN', 'OPEN',
     '开放入站摘要日志全局开关（平台可单独关闭）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('OPEN_SKEW_SECONDS', '300', 'NUMBER', 'OPEN',
     'HMAC 签名时钟偏差（秒）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('OPEN_NONCE_FAIL_CLOSED', 'true', 'BOOLEAN', 'OPEN',
     'nonce 写入 Redis 失败时是否拒绝请求（生产建议 true）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('OPEN_INCLUDE_BODY_HASH', 'true', 'BOOLEAN', 'OPEN',
     'HMAC 是否纳入 body SHA-256', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('OPEN_ROTATE_GRACE_HOURS', '24', 'NUMBER', 'OPEN',
     '密钥轮换后旧密可用宽限期（小时）；≤0 立即失效', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INGRESS_ENABLED', 'false', 'BOOLEAN', 'INGRESS',
     '已发布 API 入站防护总开关。false=信任宿主网关。停用本项后回退 yu.flow.ingress.enabled', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INGRESS_DEFAULT_AUTH_MODE', 'NONE', 'STRING', 'INGRESS',
     '默认鉴权：NONE | HOST | OPEN（接口可覆盖）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INGRESS_DEFAULT_ANTI_REPLAY', 'true', 'BOOLEAN', 'INGRESS',
     '默认防重放（仅 OPEN 鉴权生效）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INGRESS_DEFAULT_RATE_LIMIT_ENABLED', 'false', 'BOOLEAN', 'INGRESS',
     '默认是否启用接口限流', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INGRESS_DEFAULT_RATE_LIMIT_QPS', '100', 'NUMBER', 'INGRESS',
     '默认限流 QPS（秒级固定窗口）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INGRESS_DEFAULT_IP_ALLOWLIST', '', 'STRING', 'INGRESS',
     '默认 IP 白名单（空=不限制；逗号分隔 IP/CIDR）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INGRESS_RATE_LIMIT_FAIL_OPEN', 'true', 'BOOLEAN', 'INGRESS',
     '限流 Redis 失败时是否放行（fail-open）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO NOTHING;

-- =====================================================================
-- RBAC
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_sys_user (
    id            varchar(32)  NOT NULL,
    username      varchar(64)  NOT NULL,
    password_hash varchar(128) NOT NULL,
    display_name  varchar(64),
    status        smallint     NOT NULL DEFAULT 1,
    is_builtin    smallint     NOT NULL DEFAULT 0,
    remark        varchar(255),
    create_time   timestamp,
    update_time   timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);
COMMENT ON TABLE flow_sys_user IS '系统用户';

CREATE TABLE IF NOT EXISTS flow_sys_role (
    id          varchar(32)  NOT NULL,
    role_code   varchar(64)  NOT NULL,
    role_name   varchar(64)  NOT NULL,
    status      smallint     NOT NULL DEFAULT 1,
    is_builtin  smallint     NOT NULL DEFAULT 0,
    remark      varchar(255),
    create_time timestamp,
    update_time timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);
COMMENT ON TABLE flow_sys_role IS '系统角色';

CREATE TABLE IF NOT EXISTS flow_sys_permission (
    id          varchar(32)  NOT NULL,
    perm_code   varchar(128) NOT NULL,
    perm_name   varchar(64)  NOT NULL,
    group_code  varchar(64),
    remark      varchar(255),
    create_time timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_perm_code UNIQUE (perm_code)
);
COMMENT ON TABLE flow_sys_permission IS '权限点';

CREATE TABLE IF NOT EXISTS flow_sys_user_role (
    user_id varchar(32) NOT NULL,
    role_id varchar(32) NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE flow_sys_user_role IS '用户-角色';
CREATE INDEX IF NOT EXISTS idx_user_role_role ON flow_sys_user_role (role_id);

CREATE TABLE IF NOT EXISTS flow_sys_role_permission (
    role_id   varchar(32)  NOT NULL,
    perm_code varchar(128) NOT NULL,
    PRIMARY KEY (role_id, perm_code)
);
COMMENT ON TABLE flow_sys_role_permission IS '角色-权限';
CREATE INDEX IF NOT EXISTS idx_role_perm_code ON flow_sys_role_permission (perm_code);

INSERT INTO flow_sys_role (id, role_code, role_name, status, is_builtin, remark, create_time, update_time)
VALUES
    ('role_admin', 'ADMIN', '管理员', 1, 1, '全部权限', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role_operator', 'OPERATOR', '运维员', 1, 1, '编排与观测，无用户/系统配置写', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role_viewer', 'VIEWER', '只读员', 1, 1, '只读查看', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET role_name = EXCLUDED.role_name;

INSERT INTO flow_sys_permission (id, perm_code, perm_name, group_code, remark, create_time)
VALUES
    ('p_home', 'home:view', '首页', 'home', NULL, CURRENT_TIMESTAMP),
    ('p_api_v', 'flow:api:view', '接口查看', 'flow', NULL, CURRENT_TIMESTAMP),
    ('p_api_w', 'flow:api:write', '接口编排', 'flow', NULL, CURRENT_TIMESTAMP),
    ('p_task_v', 'flow:task:view', '任务查看', 'flow', NULL, CURRENT_TIMESTAMP),
    ('p_task_w', 'flow:task:write', '任务编排', 'flow', NULL, CURRENT_TIMESTAMP),
    ('p_svc_v', 'flow:service:view', '服务查看', 'flow', NULL, CURRENT_TIMESTAMP),
    ('p_svc_w', 'flow:service:write', '服务编排', 'flow', NULL, CURRENT_TIMESTAMP),
    ('p_page_v', 'flow:page:view', '页面查看', 'flow', NULL, CURRENT_TIMESTAMP),
    ('p_page_w', 'flow:page:write', '页面设计', 'flow', NULL, CURRENT_TIMESTAMP),
    ('p_rt_v', 'flow:runtime:view', '运行中心', 'ops', NULL, CURRENT_TIMESTAMP),
    ('p_open_v', 'flow:open:view', '开放平台查看', 'ops', NULL, CURRENT_TIMESTAMP),
    ('p_open_w', 'flow:open:write', '开放平台管理', 'ops', NULL, CURRENT_TIMESTAMP),
    ('p_log_v', 'log:view', '日志中心', 'ops', NULL, CURRENT_TIMESTAMP),
    ('p_ds_v', 'flow:ds:view', '数据源查看', 'infra', NULL, CURRENT_TIMESTAMP),
    ('p_ds_w', 'flow:ds:write', '数据源管理', 'infra', NULL, CURRENT_TIMESTAMP),
    ('p_model_v', 'flow:model:view', '模型查看', 'infra', NULL, CURRENT_TIMESTAMP),
    ('p_model_w', 'flow:model:write', '模型管理', 'infra', NULL, CURRENT_TIMESTAMP),
    ('p_tpl_v', 'sys:template:view', '响应模板查看', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_tpl_w', 'sys:template:write', '响应模板管理', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_macro_v', 'sys:macro:view', '全局参数查看', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_macro_w', 'sys:macro:write', '全局参数管理', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_cfg_v', 'sys:config:view', '系统配置查看', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_cfg_w', 'sys:config:write', '系统配置管理', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_user_v', 'sys:user:view', '用户查看', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_user_w', 'sys:user:write', '用户管理', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_docs', 'docs:view', '接口文档', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_all', '*', '全部权限', 'sys', 'ADMIN 超权', CURRENT_TIMESTAMP),
    ('p_role_v', 'sys:role:view', '角色查看', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_role_w', 'sys:role:write', '角色管理', 'sys', NULL, CURRENT_TIMESTAMP),
    ('p_alert_v', 'flow:alert:view', '告警查看', 'ops', NULL, CURRENT_TIMESTAMP),
    ('p_alert_w', 'flow:alert:edit', '告警管理', 'ops', NULL, CURRENT_TIMESTAMP),
    ('p_release_v', 'flow:release:view', '发布门禁查看', 'ops', NULL, CURRENT_TIMESTAMP),
    ('p_release_w', 'flow:release:edit', '发布门禁管理', 'ops', NULL, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET perm_name = EXCLUDED.perm_name;

INSERT INTO flow_sys_role_permission (role_id, perm_code)
VALUES ('role_admin', '*')
ON CONFLICT (role_id, perm_code) DO NOTHING;

INSERT INTO flow_sys_role_permission (role_id, perm_code)
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
    ('role_operator', 'docs:view'),
    ('role_operator', 'flow:alert:view'),
    ('role_operator', 'flow:alert:edit'),
    ('role_operator', 'flow:release:view'),
    ('role_operator', 'flow:release:edit')
ON CONFLICT (role_id, perm_code) DO NOTHING;

INSERT INTO flow_sys_role_permission (role_id, perm_code)
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
    ('role_viewer', 'docs:view'),
    ('role_viewer', 'flow:alert:view'),
    ('role_viewer', 'flow:release:view')
ON CONFLICT (role_id, perm_code) DO NOTHING;

INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('RBAC_ENABLED', 'true', 'BOOLEAN', 'SECURITY',
     '是否启用数据库用户 RBAC；false 时登录回退 yu.flow.username/password', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('INGRESS_DEFAULT_TIMEOUT_MS', '30000', 'NUMBER', 'INGRESS',
     '已发布 API 默认执行超时（毫秒）。≤0 不限制。接口 securityConfig.timeoutMs 可覆盖，改完需发布', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('ALERT_ENABLED', 'false', 'BOOLEAN', 'ALERT',
     '运行告警总开关。开启后按间隔扫描近窗口异常并推送 Webhook', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ALERT_WEBHOOK_URL', '', 'STRING', 'ALERT',
     'Webhook URL（钉钉/企微自定义机器人或任意 HTTP 接收端）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ALERT_INTERVAL_MINUTES', '15', 'NUMBER', 'ALERT',
     '扫描间隔（分钟）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ALERT_TOP_N', '10', 'NUMBER', 'ALERT',
     '每次最多推送异常条数', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ALERT_WINDOW', '24h', 'STRING', 'ALERT',
     '指标窗口：1h / 24h / 7d', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ALERT_MIN_HEALTH', 'error', 'STRING', 'ALERT',
     '最低告警健康度：error 仅严重；warn 含预警', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ALERT_DEDUP_MINUTES', '60', 'NUMBER', 'ALERT',
     '同一资产告警去重静默（分钟）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO NOTHING;

-- =====================================================================
-- audit log
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_log_audit (
    id          varchar(64)  NOT NULL,
    action      varchar(64)  NOT NULL,
    operator    varchar(100),
    target_type varchar(64),
    target_id   varchar(64),
    detail      varchar(1024),
    create_time timestamp,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_audit IS '配置变更审计';
CREATE INDEX IF NOT EXISTS idx_audit_action_time ON flow_log_audit (action, create_time);
CREATE INDEX IF NOT EXISTS idx_audit_operator ON flow_log_audit (operator);

-- =====================================================================
-- sys_config.sort_order
-- =====================================================================
ALTER TABLE flow_sys_config ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 100;

UPDATE flow_sys_config SET sort_order = 10 WHERE config_key = 'ALERT_ENABLED';
UPDATE flow_sys_config SET sort_order = 20 WHERE config_key = 'ALERT_WEBHOOK_URL';
UPDATE flow_sys_config SET sort_order = 30 WHERE config_key = 'ALERT_INTERVAL_MINUTES';
UPDATE flow_sys_config SET sort_order = 40 WHERE config_key = 'ALERT_TOP_N';
UPDATE flow_sys_config SET sort_order = 50 WHERE config_key = 'ALERT_WINDOW';
UPDATE flow_sys_config SET sort_order = 60 WHERE config_key = 'ALERT_MIN_HEALTH';
UPDATE flow_sys_config SET sort_order = 70 WHERE config_key = 'ALERT_DEDUP_MINUTES';

UPDATE flow_sys_config SET sort_order = 10 WHERE config_key = 'INGRESS_ENABLED';
UPDATE flow_sys_config SET sort_order = 20 WHERE config_key = 'INGRESS_DEFAULT_AUTH_MODE';
UPDATE flow_sys_config SET sort_order = 30 WHERE config_key = 'INGRESS_DEFAULT_ANTI_REPLAY';
UPDATE flow_sys_config SET sort_order = 40 WHERE config_key = 'INGRESS_DEFAULT_RATE_LIMIT_ENABLED';
UPDATE flow_sys_config SET sort_order = 50 WHERE config_key = 'INGRESS_DEFAULT_RATE_LIMIT_QPS';
UPDATE flow_sys_config SET sort_order = 60 WHERE config_key = 'INGRESS_DEFAULT_IP_ALLOWLIST';
UPDATE flow_sys_config SET sort_order = 70 WHERE config_key = 'INGRESS_RATE_LIMIT_FAIL_OPEN';
UPDATE flow_sys_config SET sort_order = 80 WHERE config_key = 'INGRESS_DEFAULT_TIMEOUT_MS';

UPDATE flow_sys_config SET sort_order = 10 WHERE config_key = 'OPEN_ENABLED';
UPDATE flow_sys_config SET sort_order = 20 WHERE config_key = 'OPEN_ALLOW_PLAIN_SECRET';
UPDATE flow_sys_config SET sort_order = 30 WHERE config_key = 'OPEN_ALLOW_DIRECT_PATH';
UPDATE flow_sys_config SET sort_order = 40 WHERE config_key = 'OPEN_REQUIRE_HOST_AUTH';
UPDATE flow_sys_config SET sort_order = 50 WHERE config_key = 'OPEN_CALL_LOG_ENABLED';
UPDATE flow_sys_config SET sort_order = 60 WHERE config_key = 'OPEN_SKEW_SECONDS';
UPDATE flow_sys_config SET sort_order = 70 WHERE config_key = 'OPEN_NONCE_FAIL_CLOSED';
UPDATE flow_sys_config SET sort_order = 80 WHERE config_key = 'OPEN_INCLUDE_BODY_HASH';
UPDATE flow_sys_config SET sort_order = 90 WHERE config_key = 'OPEN_ROTATE_GRACE_HOURS';

UPDATE flow_sys_config SET sort_order = 10 WHERE config_key = 'RBAC_ENABLED';
UPDATE flow_sys_config SET sort_order = 20 WHERE config_key = 'LOGIN_MAX_RETRY';
UPDATE flow_sys_config SET sort_order = 30 WHERE config_key = 'LOGIN_LOCK_DURATION';
UPDATE flow_sys_config SET sort_order = 40 WHERE config_key = 'TOKEN_EXPIRE';
UPDATE flow_sys_config SET sort_order = 50 WHERE config_key = 'TOKEN_REFRESH_EXPIRE';

-- =====================================================================
-- alert product
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_alert_channel (
    id            varchar(64)  NOT NULL,
    name          varchar(100) NOT NULL,
    type          varchar(20)  NOT NULL,
    config_json   text,
    enabled       smallint     NOT NULL DEFAULT 1,
    create_time   timestamp,
    update_time   timestamp,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_channel IS '告警通道';
CREATE INDEX IF NOT EXISTS idx_alert_channel_type ON flow_alert_channel (type);

CREATE TABLE IF NOT EXISTS flow_alert_rule (
    id                  varchar(64)  NOT NULL,
    name                varchar(100) NOT NULL,
    enabled             smallint     NOT NULL DEFAULT 1,
    scope_asset_types   varchar(200),
    "window"              varchar(20)  NOT NULL DEFAULT '24h',
    min_health          varchar(20)  NOT NULL DEFAULT 'error',
    top_n               integer      NOT NULL DEFAULT 10,
    channel_ids         varchar(500),
    interval_minutes    integer      NOT NULL DEFAULT 15,
    dedup_minutes       integer      NOT NULL DEFAULT 60,
    create_time         timestamp,
    update_time         timestamp,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_rule IS '告警规则';
CREATE INDEX IF NOT EXISTS idx_alert_rule_enabled ON flow_alert_rule (enabled);

CREATE TABLE IF NOT EXISTS flow_alert_event (
    id            varchar(64)  NOT NULL,
    rule_id       varchar(64),
    rule_name     varchar(100),
    fingerprint   varchar(200),
    asset_type    varchar(32),
    asset_id      varchar(64),
    asset_name    varchar(200),
    health        varchar(20),
    error_rate    double precision,
    fail_count    bigint,
    "window"        varchar(20),
    channel_type  varchar(20),
    channel_id    varchar(64),
    status        varchar(20)  NOT NULL,
    payload_json  text,
    error_msg     varchar(500),
    fired_at      timestamp    NOT NULL,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_event IS '告警事件历史';
CREATE INDEX IF NOT EXISTS idx_alert_event_fired ON flow_alert_event (fired_at);
CREATE INDEX IF NOT EXISTS idx_alert_event_rule ON flow_alert_event (rule_id);
CREATE INDEX IF NOT EXISTS idx_alert_event_status ON flow_alert_event (status);

-- =====================================================================
-- backfill directory biz_type
-- =====================================================================
UPDATE flow_directory d
SET biz_type = 'api'
WHERE (d.biz_type IS NULL OR d.biz_type = '')
  AND EXISTS (SELECT 1 FROM flow_api_info a WHERE a.directory_id = d.id)
  AND NOT EXISTS (SELECT 1 FROM flow_task_info t WHERE t.directory_id = d.id)
  AND NOT EXISTS (SELECT 1 FROM flow_service_info s WHERE s.directory_id = d.id);

UPDATE flow_directory d
SET biz_type = 'task'
WHERE (d.biz_type IS NULL OR d.biz_type = '')
  AND EXISTS (SELECT 1 FROM flow_task_info t WHERE t.directory_id = d.id)
  AND NOT EXISTS (SELECT 1 FROM flow_api_info a WHERE a.directory_id = d.id)
  AND NOT EXISTS (SELECT 1 FROM flow_service_info s WHERE s.directory_id = d.id);

UPDATE flow_directory d
SET biz_type = 'service'
WHERE (d.biz_type IS NULL OR d.biz_type = '')
  AND EXISTS (SELECT 1 FROM flow_service_info s WHERE s.directory_id = d.id)
  AND NOT EXISTS (SELECT 1 FROM flow_api_info a WHERE a.directory_id = d.id)
  AND NOT EXISTS (SELECT 1 FROM flow_task_info t WHERE t.directory_id = d.id);

-- =====================================================================
-- mail / log retention seeds
-- =====================================================================
INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, sort_order, create_time, update_time)
VALUES
    ('MAIL_ENABLED', 'false', 'BOOLEAN', 'MAIL',
     '邮件发送总开关。关闭后告警 Email 通道与后续邮件节点均不可发信', 1, 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MAIL_HOST', '', 'STRING', 'MAIL',
     'SMTP 主机，如 smtp.qq.com / smtp.163.com', 1, 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MAIL_PORT', '465', 'NUMBER', 'MAIL',
     'SMTP 端口：SSL 常用 465，STARTTLS 常用 587', 1, 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MAIL_USERNAME', '', 'STRING', 'MAIL',
     'SMTP 登录账号（通常为邮箱地址）', 1, 1, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MAIL_PASSWORD', '', 'STRING', 'MAIL',
     'SMTP 密码或应用专用密码', 1, 1, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MAIL_FROM', '', 'STRING', 'MAIL',
     '发件人地址；为空则使用 MAIL_USERNAME', 1, 1, 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MAIL_SSL', 'true', 'BOOLEAN', 'MAIL',
     '启用 SMTPS/SSL（465）', 1, 1, 70, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MAIL_STARTTLS', 'false', 'BOOLEAN', 'MAIL',
     '启用 STARTTLS（587）；与 SSL 二选一为主', 1, 1, 80, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO UPDATE SET
    remark = EXCLUDED.remark,
    sort_order = EXCLUDED.sort_order;

INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, sort_order, create_time, update_time)
VALUES
    ('LOG_EXECUTION_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     'API 执行日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LOG_LOGIN_RETENTION_DAYS', '90', 'NUMBER', 'LOG',
     '登录审计日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LOG_TASK_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '定时任务执行日志保留天数（flow_log_task；0 = 不清理）', 1, 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LOG_SERVICE_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '服务编排执行日志保留天数（flow_log_service；0 = 不清理）', 1, 1, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LOG_THIRD_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '第三方调用日志保留天数（flow_log_third；0 = 不清理）', 1, 1, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LOG_OPEN_CALL_RETENTION_DAYS', '30', 'NUMBER', 'LOG',
     '开放平台调用日志保留天数（flow_log_open_call；0 = 不清理）', 1, 1, 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LOG_AUDIT_RETENTION_DAYS', '180', 'NUMBER', 'LOG',
     '配置变更审计日志保留天数（flow_log_audit；0 = 不清理）', 1, 1, 70, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('LOG_ALERT_EVENT_RETENTION_DAYS', '90', 'NUMBER', 'LOG',
     '告警历史事件保留天数（flow_alert_event；0 = 不清理）', 1, 1, 80, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO UPDATE SET
    remark = EXCLUDED.remark,
    sort_order = EXCLUDED.sort_order;

-- =====================================================================
-- datasource wall + DEFAULT sentinel (postgresql)
-- =====================================================================
ALTER TABLE flow_datasource ADD COLUMN IF NOT EXISTS wall_config text;
ALTER TABLE flow_datasource ADD COLUMN IF NOT EXISTS is_system smallint NOT NULL DEFAULT 0;

INSERT INTO flow_datasource (
    id, name, code, db_type, driver_class_name, url, username, password,
    initial_size, min_idle, max_active, status, wall_config, is_system,
    health_status, error_count, create_time, update_time
) VALUES (
    'ds_system_default',
    '系统默认数据源',
    '[DEFAULT]',
    'postgresql',
    'org.postgresql.Driver',
    '',
    '',
    '',
    5, 5, 20, 1,
    '{"enabled":true,"multiStatementAllow":false,"commentAllow":false,"noneBaseStatementAllow":false,"selectAllow":true,"insertAllow":true,"updateAllow":true,"deleteAllow":true,"tableCheck":true,"tableWhiteList":[],"tableBlackList":[],"functionBlackList":["sleep","benchmark","load_file","updatexml","extractvalue","pg_sleep"],"variantCheck":true}',
    1,
    'UNKNOWN',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    is_system = 1,
    name = EXCLUDED.name;

UPDATE flow_datasource
SET wall_config = '{"enabled":true,"multiStatementAllow":false,"commentAllow":false,"noneBaseStatementAllow":false,"selectAllow":true,"insertAllow":true,"updateAllow":true,"deleteAllow":true,"tableCheck":true,"tableWhiteList":[],"tableBlackList":[],"functionBlackList":["sleep","benchmark","load_file","updatexml","extractvalue","pg_sleep"],"variantCheck":true}',
    update_time = CURRENT_TIMESTAMP
WHERE code = '[DEFAULT]'
  AND is_system = 1;

-- =====================================================================
-- harden ingress defaults
-- =====================================================================
UPDATE flow_sys_config
SET config_value = 'true',
    remark      = '已发布 API 入站防护总开关（安全默认开启；false=关闭后网关仍强制管理端 JWT）',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'INGRESS_ENABLED';

UPDATE flow_sys_config
SET config_value = 'HOST',
    remark      = '默认鉴权模式：NONE|HOST|OPEN（安全默认 HOST，需管理端 JWT）',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'INGRESS_DEFAULT_AUTH_MODE';

UPDATE flow_sys_config
SET config_value = 'true',
    remark      = '无 AppKey 访问已发布 API 时是否强制宿主登录（JWT）',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'OPEN_REQUIRE_HOST_AUTH';

-- =====================================================================
-- release gate
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_env (
    id                   varchar(64)  NOT NULL,
    code                 varchar(32)  NOT NULL,
    name                 varchar(100) NOT NULL,
    require_suite_pass   smallint     NOT NULL DEFAULT 0,
    pass_ttl_hours       integer      NOT NULL DEFAULT 24,
    enabled              smallint     NOT NULL DEFAULT 1,
    sort_order           integer      NOT NULL DEFAULT 0,
    remark               varchar(500),
    create_time          timestamp,
    update_time          timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_flow_env_code UNIQUE (code)
);
COMMENT ON TABLE flow_env IS '发布逻辑环境';

CREATE TABLE IF NOT EXISTS flow_regression_suite (
    id            varchar(64)  NOT NULL,
    name          varchar(100) NOT NULL,
    asset_type    varchar(32)  NOT NULL,
    asset_id      varchar(64)  NOT NULL,
    enabled       smallint     NOT NULL DEFAULT 1,
    create_time   timestamp,
    update_time   timestamp,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_suite IS '回归测试套件';
CREATE INDEX IF NOT EXISTS idx_reg_suite_asset ON flow_regression_suite (asset_type, asset_id);

CREATE TABLE IF NOT EXISTS flow_regression_case (
    id                  varchar(64)  NOT NULL,
    suite_id            varchar(64)  NOT NULL,
    name                varchar(100) NOT NULL,
    sort_order          integer      NOT NULL DEFAULT 0,
    enabled             smallint     NOT NULL DEFAULT 1,
    headers_json        text,
    query_json          text,
    body                text,
    expect_trace_status varchar(20),
    expect_json_path    varchar(128),
    expect_value        varchar(500),
    timeout_ms          integer      NOT NULL DEFAULT 10000,
    create_time         timestamp,
    update_time         timestamp,
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_case IS '回归用例';
CREATE INDEX IF NOT EXISTS idx_reg_case_suite ON flow_regression_case (suite_id);

CREATE TABLE IF NOT EXISTS flow_regression_run (
    id            varchar(64)  NOT NULL,
    suite_id      varchar(64)  NOT NULL,
    asset_type    varchar(32)  NOT NULL,
    asset_id      varchar(64)  NOT NULL,
    env_code      varchar(32)  NOT NULL,
    status        varchar(20)  NOT NULL,
    total_cases   integer      NOT NULL DEFAULT 0,
    passed_cases  integer      NOT NULL DEFAULT 0,
    failed_cases  integer      NOT NULL DEFAULT 0,
    started_at    timestamp    NOT NULL,
    finished_at   timestamp,
    triggered_by  varchar(100),
    summary       varchar(500),
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_run IS '回归运行记录';
CREATE INDEX IF NOT EXISTS idx_reg_run_asset_env ON flow_regression_run (asset_type, asset_id, env_code, finished_at);
CREATE INDEX IF NOT EXISTS idx_reg_run_suite ON flow_regression_run (suite_id);

CREATE TABLE IF NOT EXISTS flow_regression_run_case (
    id            varchar(64)  NOT NULL,
    run_id        varchar(64)  NOT NULL,
    case_id       varchar(64)  NOT NULL,
    case_name     varchar(100),
    status        varchar(20)  NOT NULL,
    duration_ms   bigint,
    message       varchar(500),
    detail_json   varchar(2000),
    PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_run_case IS '回归用例运行明细';
CREATE INDEX IF NOT EXISTS idx_reg_run_case_run ON flow_regression_run_case (run_id);

INSERT INTO flow_env (id, code, name, require_suite_pass, pass_ttl_hours, enabled, sort_order, remark, create_time, update_time)
VALUES
    ('env_dev', 'DEV', '开发', 0, 72, 1, 10, '默认发布环境，不强制回归', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('env_staging', 'STAGING', '预发', 1, 48, 1, 20, '发布前需回归通过', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('env_prod', 'PROD', '生产', 1, 24, 1, 30, '发布前需回归通过（24h 内）', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- =====================================================================
-- API excel template
-- =====================================================================
CREATE TABLE IF NOT EXISTS flow_api_excel_template (
    id            varchar(64)  NOT NULL,
    api_id        varchar(64)  NOT NULL,
    file_name     varchar(255) NOT NULL,
    content_type  varchar(120),
    content       bytea        NOT NULL,
    file_size     integer      NOT NULL DEFAULT 0,
    create_time   timestamp,
    update_time   timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_api_excel_tpl_api UNIQUE (api_id)
);
COMMENT ON TABLE flow_api_excel_template IS 'API Excel 导出模板';

-- =====================================================================
-- COLUMN COMMENTS (auto)
-- =====================================================================

-- flow_alert_channel
COMMENT ON COLUMN flow_alert_channel.config_json IS '通道配置 JSON';
COMMENT ON COLUMN flow_alert_channel.enabled IS '1启用 0停用';
COMMENT ON COLUMN flow_alert_channel.id IS '主键';
COMMENT ON COLUMN flow_alert_channel.name IS '通道名称';
COMMENT ON COLUMN flow_alert_channel.type IS 'WEBHOOK | EMAIL';

-- flow_alert_event
COMMENT ON COLUMN flow_alert_event.fingerprint IS '去重指纹';
COMMENT ON COLUMN flow_alert_event.id IS '主键';
COMMENT ON COLUMN flow_alert_event.rule_id IS '规则 ID，SysConfig 兜底为空';
COMMENT ON COLUMN flow_alert_event.status IS 'SUCCESS|FAIL|SUPPRESSED';

-- flow_alert_rule
COMMENT ON COLUMN flow_alert_rule.channel_ids IS '通道 ID JSON 数组';
COMMENT ON COLUMN flow_alert_rule.enabled IS '1启用 0停用';
COMMENT ON COLUMN flow_alert_rule.id IS '主键';
COMMENT ON COLUMN flow_alert_rule.min_health IS 'error|warn';
COMMENT ON COLUMN flow_alert_rule.name IS '规则名称';
COMMENT ON COLUMN flow_alert_rule.scope_asset_types IS '资产类型 CSV：API,TASK,SERVICE,PLATFORM；空=全部';
COMMENT ON COLUMN flow_alert_rule."window" IS '1h/24h/7d';

-- flow_api_excel_template
COMMENT ON COLUMN flow_api_excel_template.api_id IS '接口 ID';
COMMENT ON COLUMN flow_api_excel_template.content IS 'xlsx 二进制';
COMMENT ON COLUMN flow_api_excel_template.content_type IS 'MIME';
COMMENT ON COLUMN flow_api_excel_template.file_name IS '原始文件名';
COMMENT ON COLUMN flow_api_excel_template.id IS '主键';

-- flow_api_info
COMMENT ON COLUMN flow_api_info.cache_config IS '响应缓存配置 JSON：enabled/ttlSeconds/keyParams/includePageable';
COMMENT ON COLUMN flow_api_info.security_config IS '入站防护 JSON：authMode/antiReplay/rateLimit/ipAllowlist';
COMMENT ON COLUMN flow_api_info.view_export_config IS '数据查看与导出 JSON：columns/sheetName/maxExportRows';

-- flow_asset_version
COMMENT ON COLUMN flow_asset_version.asset_id IS '资产ID';
COMMENT ON COLUMN flow_asset_version.biz_type IS '资产类型：api / task / service';
COMMENT ON COLUMN flow_asset_version.create_time IS '创建时间';
COMMENT ON COLUMN flow_asset_version.id IS '雪花ID';
COMMENT ON COLUMN flow_asset_version.publish_time IS '发布时间';
COMMENT ON COLUMN flow_asset_version.publisher IS '发布人';
COMMENT ON COLUMN flow_asset_version.remark IS '备注';
COMMENT ON COLUMN flow_asset_version.snapshot IS '发布快照 JSON（与各模块 published_snapshot 同构）';
COMMENT ON COLUMN flow_asset_version.source IS '来源：publish / rollback';
COMMENT ON COLUMN flow_asset_version.version_no IS '同资产内递增版本号';

-- flow_datasource
COMMENT ON COLUMN flow_datasource.is_system IS '系统数据源(1=不可删改连接，如[DEFAULT])';
COMMENT ON COLUMN flow_datasource.wall_config IS 'SQL安全墙JSON(DataSourceWallConfig)';

-- flow_directory
COMMENT ON COLUMN flow_directory.biz_type IS '业务域：api/task/service/model/page，空=共用';

-- flow_env
COMMENT ON COLUMN flow_env.code IS 'DEV|STAGING|PROD';
COMMENT ON COLUMN flow_env.id IS '主键';
COMMENT ON COLUMN flow_env.name IS '显示名';
COMMENT ON COLUMN flow_env.pass_ttl_hours IS '通过结果有效小时数';
COMMENT ON COLUMN flow_env.require_suite_pass IS '1=发布前需回归通过';

-- flow_log_audit
COMMENT ON COLUMN flow_log_audit.action IS 'API_PUBLISH|SYS_CONFIG_UPDATE|OPEN_SECRET_ROTATE';
COMMENT ON COLUMN flow_log_audit.detail IS 'JSON 摘要，不含密钥';
COMMENT ON COLUMN flow_log_audit.operator IS '操作人';
COMMENT ON COLUMN flow_log_audit.target_type IS 'API|SYS_CONFIG|OPEN_CREDENTIAL';

-- flow_log_open_call
COMMENT ON COLUMN flow_log_open_call.api_id IS '命中的 API ID';
COMMENT ON COLUMN flow_log_open_call.app_key IS '调用方 AppKey';
COMMENT ON COLUMN flow_log_open_call.cost_ms IS '耗时毫秒';
COMMENT ON COLUMN flow_log_open_call.error_code IS '业务/鉴权错误码';
COMMENT ON COLUMN flow_log_open_call.id IS '雪花ID';
COMMENT ON COLUMN flow_log_open_call.method IS 'HTTP 方法';
COMMENT ON COLUMN flow_log_open_call.path IS '真实发布路径';
COMMENT ON COLUMN flow_log_open_call.platform_id IS '开放平台ID';
COMMENT ON COLUMN flow_log_open_call.request_id IS '请求追踪ID';
COMMENT ON COLUMN flow_log_open_call.status IS 'HTTP 状态';

-- flow_log_service
COMMENT ON COLUMN flow_log_service.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_log_service.create_time IS '执行开始时间';
COMMENT ON COLUMN flow_log_service.error_msg IS '失败信息';
COMMENT ON COLUMN flow_log_service.id IS '雪花ID';
COMMENT ON COLUMN flow_log_service.service_id IS '关联服务ID';
COMMENT ON COLUMN flow_log_service.service_name IS '服务名称（冗余）';
COMMENT ON COLUMN flow_log_service.status IS '执行状态：SUCCESS / FAILED / RUNNING';
COMMENT ON COLUMN flow_log_service.trace_data IS 'FlowTrace JSON 快照（logEnabled=true 时记录）';
COMMENT ON COLUMN flow_log_service.trigger_type IS '触发类型：MANUAL=手动, CALL=流程内调用, DEBUG=调试';

-- flow_log_third
COMMENT ON COLUMN flow_log_third.api_type IS '接口标识';
COMMENT ON COLUMN flow_log_third.create_time IS '创建时间';
COMMENT ON COLUMN flow_log_third.curl IS 'Curl命令';
COMMENT ON COLUMN flow_log_third.elapsed_time IS '请求耗时(ms)';
COMMENT ON COLUMN flow_log_third.error_message IS '错误信息';
COMMENT ON COLUMN flow_log_third.id IS '主键';
COMMENT ON COLUMN flow_log_third.is_success IS '是否成功（0失败/1成功）';
COMMENT ON COLUMN flow_log_third.request_headers IS '请求头';
COMMENT ON COLUMN flow_log_third.request_method IS '请求方法';
COMMENT ON COLUMN flow_log_third.request_params IS '请求参数';
COMMENT ON COLUMN flow_log_third.request_url IS '请求URL';
COMMENT ON COLUMN flow_log_third.response_body IS '响应内容';
COMMENT ON COLUMN flow_log_third.response_status IS '响应状态码';
COMMENT ON COLUMN flow_log_third.source IS '调用来源：API/TASK/DEBUG/OTHER';
COMMENT ON COLUMN flow_log_third.source_name IS '来源名称（接口名/任务名）';
COMMENT ON COLUMN flow_log_third.source_ref IS '来源关联ID（apiId/taskId）';

-- flow_metrics_meta
COMMENT ON COLUMN flow_metrics_meta.asset_id IS '资产ID';
COMMENT ON COLUMN flow_metrics_meta.asset_type IS 'API / TASK / SERVICE / PLATFORM';
COMMENT ON COLUMN flow_metrics_meta.consec_fail IS '连续业务失败次数';
COMMENT ON COLUMN flow_metrics_meta.id IS '雪花ID';
COMMENT ON COLUMN flow_metrics_meta.last_fail_at IS '最近业务失败 epoch ms';
COMMENT ON COLUMN flow_metrics_meta.last_success_at IS '最近成功 epoch ms';
COMMENT ON COLUMN flow_metrics_meta.update_time IS '最后更新时间';

-- flow_metrics_minute
COMMENT ON COLUMN flow_metrics_minute.asset_id IS '资产ID';
COMMENT ON COLUMN flow_metrics_minute.asset_type IS 'API / TASK / SERVICE';
COMMENT ON COLUMN flow_metrics_minute.auth_fail_cnt IS '鉴权失败次数（如开放平台 401/403）';
COMMENT ON COLUMN flow_metrics_minute.bucket_start IS '分钟桶起点（整分）';
COMMENT ON COLUMN flow_metrics_minute.hist_json IS '直方图桶计数 JSON 数组';
COMMENT ON COLUMN flow_metrics_minute.id IS '雪花ID';
COMMENT ON COLUMN flow_metrics_minute.trigger_type IS '触发类型；API 固定为 _';
COMMENT ON COLUMN flow_metrics_minute.update_time IS '最后更新时间';

-- flow_open_api_grant
COMMENT ON COLUMN flow_open_api_grant.allow_methods IS '空=跟随接口方法';

-- flow_open_credential
COMMENT ON COLUMN flow_open_credential.app_secret_enc IS 'AES加密后的secret';
COMMENT ON COLUMN flow_open_credential.rotated_from_id IS '轮换来源凭证';
COMMENT ON COLUMN flow_open_credential.secret_hint IS '末4位提示';
COMMENT ON COLUMN flow_open_credential.status IS '0停用 1启用 2已轮换废弃';

-- flow_open_platform
COMMENT ON COLUMN flow_open_platform.code IS '唯一编码';
COMMENT ON COLUMN flow_open_platform.contact IS '联系人';
COMMENT ON COLUMN flow_open_platform.expire_at IS '平台到期时间';
COMMENT ON COLUMN flow_open_platform.id IS '雪花ID';
COMMENT ON COLUMN flow_open_platform.ip_allowlist IS 'IP白名单JSON数组，空=不限';
COMMENT ON COLUMN flow_open_platform.name IS '平台名称';
COMMENT ON COLUMN flow_open_platform.open_call_log_enabled IS '是否记录入站摘要日志 0关1开';
COMMENT ON COLUMN flow_open_platform.rate_limit_qps IS '平台级 QPS 上限，空=不限';
COMMENT ON COLUMN flow_open_platform.remark IS '备注';
COMMENT ON COLUMN flow_open_platform.status IS '0停用 1启用';

-- flow_regression_case
COMMENT ON COLUMN flow_regression_case.body IS '请求体，≤32KB';
COMMENT ON COLUMN flow_regression_case.expect_json_path IS '简单 JSONPath';
COMMENT ON COLUMN flow_regression_case.expect_trace_status IS 'success|error，空则不校验';
COMMENT ON COLUMN flow_regression_case.expect_value IS '期望值（字符串比较）';
COMMENT ON COLUMN flow_regression_case.headers_json IS '请求头 JSON（禁止敏感头）';
COMMENT ON COLUMN flow_regression_case.query_json IS 'Query JSON';

-- flow_regression_run
COMMENT ON COLUMN flow_regression_run.status IS 'RUNNING|PASSED|FAILED|ERROR';

-- flow_regression_run_case
COMMENT ON COLUMN flow_regression_run_case.detail_json IS '截断后的摘要，不含全量响应';
COMMENT ON COLUMN flow_regression_run_case.status IS 'PASSED|FAILED|ERROR|SKIPPED';

-- flow_regression_suite
COMMENT ON COLUMN flow_regression_suite.asset_type IS 'API|TASK|SERVICE';

-- flow_service_info
COMMENT ON COLUMN flow_service_info.contract IS '服务契约 JSON：inputs/outputs/outputDescription';
COMMENT ON COLUMN flow_service_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_service_info.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_service_info.directory_id IS '关联目录ID（复用全局目录树）';
COMMENT ON COLUMN flow_service_info.dsl_content IS '流程定义 DSL JSON（草稿）';
COMMENT ON COLUMN flow_service_info.enabled IS '启用状态：0=停用, 1=启用';
COMMENT ON COLUMN flow_service_info.id IS '雪花ID';
COMMENT ON COLUMN flow_service_info.info IS '服务描述';
COMMENT ON COLUMN flow_service_info.log_enabled IS '是否记录执行日志';
COMMENT ON COLUMN flow_service_info.name IS '服务名称';
COMMENT ON COLUMN flow_service_info.publish_status IS '发布状态：0=未发布, 1=已发布';
COMMENT ON COLUMN flow_service_info.publish_time IS '最近发布时间';
COMMENT ON COLUMN flow_service_info.published_snapshot IS '发布快照 JSON：dslContent/contract';
COMMENT ON COLUMN flow_service_info.tags IS '标签，英文逗号分隔';
COMMENT ON COLUMN flow_service_info.update_time IS '更新时间';

-- flow_sys_config
COMMENT ON COLUMN flow_sys_config.sort_order IS '组内展示顺序，越小越靠前';

-- flow_sys_permission
COMMENT ON COLUMN flow_sys_permission.group_code IS '分组：flow/ops/infra/sys';
COMMENT ON COLUMN flow_sys_permission.perm_code IS '如 flow:api:write';

-- flow_sys_role
COMMENT ON COLUMN flow_sys_role.role_code IS 'ADMIN/OPERATOR/VIEWER';

-- flow_sys_user
COMMENT ON COLUMN flow_sys_user.display_name IS '显示名';
COMMENT ON COLUMN flow_sys_user.is_builtin IS '1内置不可删';
COMMENT ON COLUMN flow_sys_user.password_hash IS 'BCrypt 密码';
COMMENT ON COLUMN flow_sys_user.status IS '1启用 0停用';
COMMENT ON COLUMN flow_sys_user.username IS '登录名';

-- flow_task_info
COMMENT ON COLUMN flow_task_info.publish_status IS '发布状态：0=未发布，1=已发布';
COMMENT ON COLUMN flow_task_info.publish_time IS '最近发布时间';
COMMENT ON COLUMN flow_task_info.published_snapshot IS '发布快照 JSON：dslContent';
