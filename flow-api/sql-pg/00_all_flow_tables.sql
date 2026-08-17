-- Yu Flow PostgreSQL / 瀚高 全量建表（由 sql-pg/flow_*.sql 汇总生成，勿手工穿插重复表）
-- 生成时间: 2026-08-17T10:24:37.976Z
-- 用法: 空库执行本文件 → 再执行 00_system_init.sql
-- Boolean 映射列必须用 boolean，勿写成 smallint
--
-- 重要：CREATE TABLE IF NOT EXISTS 不会升级已存在的旧表。
-- 缺列（如 sort_order/cache_config/wall_config）时：DROP flow_* 重跑，或执行 sql/20260812_pg_align_missing_columns.sql

-- >>> flow_alert_channel.sql
-- Table: flow_alert_channel
-- 告警通道
CREATE TABLE IF NOT EXISTS flow_alert_channel (
  id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  type varchar(20) NOT NULL,
  config_json text,
  enabled smallint NOT NULL DEFAULT 1,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_channel IS '告警通道';
COMMENT ON COLUMN flow_alert_channel.id IS '主键';
COMMENT ON COLUMN flow_alert_channel.name IS '通道名称';
COMMENT ON COLUMN flow_alert_channel.type IS 'WEBHOOK | EMAIL';
COMMENT ON COLUMN flow_alert_channel.config_json IS '通道配置 JSON';
COMMENT ON COLUMN flow_alert_channel.enabled IS '1启用 0停用';
CREATE INDEX IF NOT EXISTS idx_flow_alert_channel_type ON flow_alert_channel (type);

-- >>> flow_alert_event.sql
-- Table: flow_alert_event
-- 告警事件历史
CREATE TABLE IF NOT EXISTS flow_alert_event (
  id varchar(64) NOT NULL,
  rule_id varchar(64),
  rule_name varchar(100),
  fingerprint varchar(200),
  asset_type varchar(32),
  asset_id varchar(64),
  asset_name varchar(200),
  health varchar(20),
  error_rate double precision,
  fail_count bigint,
  "window" varchar(20),
  channel_type varchar(20),
  channel_id varchar(64),
  status varchar(20) NOT NULL,
  payload_json text,
  error_msg varchar(500),
  fired_at timestamp NOT NULL,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_event IS '告警事件历史';
COMMENT ON COLUMN flow_alert_event.id IS '主键';
COMMENT ON COLUMN flow_alert_event.rule_id IS '规则 ID，SysConfig 兜底为空';
COMMENT ON COLUMN flow_alert_event.fingerprint IS '去重指纹';
COMMENT ON COLUMN flow_alert_event."window" IS '1h/24h/7d';
COMMENT ON COLUMN flow_alert_event.status IS 'SUCCESS|FAIL|SUPPRESSED';
CREATE INDEX IF NOT EXISTS idx_flow_alert_event_fired_at ON flow_alert_event (fired_at);
CREATE INDEX IF NOT EXISTS idx_flow_alert_event_rule_id ON flow_alert_event (rule_id);
CREATE INDEX IF NOT EXISTS idx_flow_alert_event_status ON flow_alert_event (status);

-- >>> flow_alert_rule.sql
-- Table: flow_alert_rule
-- 告警规则
CREATE TABLE IF NOT EXISTS flow_alert_rule (
  id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  enabled smallint NOT NULL DEFAULT 1,
  scope_asset_types varchar(200),
  "window" varchar(20) NOT NULL DEFAULT '24h',
  min_health varchar(20) NOT NULL DEFAULT 'error',
  top_n integer NOT NULL DEFAULT 10,
  channel_ids varchar(500),
  interval_minutes integer NOT NULL DEFAULT 15,
  dedup_minutes integer NOT NULL DEFAULT 60,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_rule IS '告警规则';
COMMENT ON COLUMN flow_alert_rule.id IS '主键';
COMMENT ON COLUMN flow_alert_rule.name IS '规则名称';
COMMENT ON COLUMN flow_alert_rule.enabled IS '1启用 0停用';
COMMENT ON COLUMN flow_alert_rule.scope_asset_types IS '资产类型 CSV：API,TASK,SERVICE,PLATFORM；空=全部';
COMMENT ON COLUMN flow_alert_rule."window" IS '1h/24h/7d';
COMMENT ON COLUMN flow_alert_rule.min_health IS 'error|warn';
COMMENT ON COLUMN flow_alert_rule.channel_ids IS '通道 ID JSON 数组';
CREATE INDEX IF NOT EXISTS idx_flow_alert_rule_enabled ON flow_alert_rule (enabled);

-- >>> flow_api_excel_template.sql
-- Table: flow_api_excel_template
-- API Excel 导出模板
CREATE TABLE IF NOT EXISTS flow_api_excel_template (
  id varchar(64) NOT NULL,
  api_id varchar(64) NOT NULL,
  file_name varchar(255) NOT NULL,
  content_type varchar(120),
  content bytea NOT NULL,
  file_size integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_api_excel_template_api_id UNIQUE (api_id)
);
COMMENT ON TABLE flow_api_excel_template IS 'API Excel 导出模板';
COMMENT ON COLUMN flow_api_excel_template.id IS '主键';
COMMENT ON COLUMN flow_api_excel_template.api_id IS '接口 ID';
COMMENT ON COLUMN flow_api_excel_template.file_name IS '原始文件名';
COMMENT ON COLUMN flow_api_excel_template.content_type IS 'MIME';
COMMENT ON COLUMN flow_api_excel_template.content IS 'xlsx 二进制';

-- >>> flow_api_info.sql
-- Table: flow_api_info
-- 接口配置类
CREATE TABLE IF NOT EXISTS flow_api_info (
  id bigint NOT NULL,
  name varchar(20),
  directory_id varchar(32),
  url varchar(100),
  datasource varchar(20),
  module varchar(20),
  method varchar(10),
  service_type varchar(20),
  intercept_mode varchar(16) NOT NULL DEFAULT 'REPLACE',
  host_binding text,
  response_type varchar(10),
  version varchar(20),
  config text,
  publish_status smallint,
  contract text,
  tags varchar(500),
  level integer,
  template_id varchar(32),
  custom_success_wrapper text,
  custom_page_wrapper text,
  custom_fail_wrapper text,
  info text,
  dsl_content text,
  sql_content text,
  json_content text,
  text_content text,
  published_snapshot text,
  publish_time timestamp,
  log_enabled boolean NOT NULL DEFAULT true,
  log_mode varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
  log_retention_days integer,
  cache_config text,
  security_config text,
  privacy_config text,
  view_export_config text,
  deleted integer DEFAULT 0,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_api_info IS '接口配置类';
COMMENT ON COLUMN flow_api_info.id IS '主键ID，通过Snowflake算法生成';
COMMENT ON COLUMN flow_api_info.name IS 'API配置的名称';
COMMENT ON COLUMN flow_api_info.directory_id IS '关联全局目录树';
COMMENT ON COLUMN flow_api_info.url IS 'API的URL路径';
COMMENT ON COLUMN flow_api_info.datasource IS '数据源名称';
COMMENT ON COLUMN flow_api_info.module IS '所属模块名称';
COMMENT ON COLUMN flow_api_info.method IS '请求方式：POST、PUT、GET、DELETE';
COMMENT ON COLUMN flow_api_info.service_type IS '服务驱动类型：DB、FLOW、JSON、STRING、HOST';
COMMENT ON COLUMN flow_api_info.intercept_mode IS '同名拦截：REPLACE-替换执行引擎；WRAP-包裹转发宿主';
COMMENT ON COLUMN flow_api_info.host_binding IS 'WRAP 宿主绑定 JSON：forward/targetPath/probePath';
COMMENT ON COLUMN flow_api_info.response_type IS '响应数据类型：PAGE(分页)、LIST(列表)、OBJECT(对象)';
COMMENT ON COLUMN flow_api_info.version IS 'API版本号';
COMMENT ON COLUMN flow_api_info.config IS '核心逻辑配置，存储SQL、流编排JSON或静态数据';
COMMENT ON COLUMN flow_api_info.publish_status IS '发布状态：0：未发布；1：已发布';
COMMENT ON COLUMN flow_api_info.tags IS '标签，英文都好分隔';
COMMENT ON COLUMN flow_api_info.level IS '优先级，与请求的ss-level比较，大的优先';
COMMENT ON COLUMN flow_api_info.template_id IS '基座模板ID';
COMMENT ON COLUMN flow_api_info.custom_success_wrapper IS '自定义成功返回包装';
COMMENT ON COLUMN flow_api_info.custom_page_wrapper IS '自定义分页返回包装';
COMMENT ON COLUMN flow_api_info.custom_fail_wrapper IS '自定义失败返回包装';
COMMENT ON COLUMN flow_api_info.info IS 'API配置的详细描述';
COMMENT ON COLUMN flow_api_info.dsl_content IS '逻辑编排 (FLOW) — Flow DSL JSON';
COMMENT ON COLUMN flow_api_info.sql_content IS '数据库 (DB) — SQL 脚本';
COMMENT ON COLUMN flow_api_info.json_content IS '静态 JSON (JSON) — JSON 内容';
COMMENT ON COLUMN flow_api_info.text_content IS '静态文本 (STRING) — 纯文本内容';
COMMENT ON COLUMN flow_api_info.published_snapshot IS '发布时的完整内容快照 (JSON)，运行时引擎从此字段读取';
COMMENT ON COLUMN flow_api_info.publish_time IS '最近一次发布时间';
COMMENT ON COLUMN flow_api_info.log_enabled IS '是否记录执行日志：1-开启，0-关闭';
COMMENT ON COLUMN flow_api_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';
COMMENT ON COLUMN flow_api_info.log_retention_days IS '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数';
COMMENT ON COLUMN flow_api_info.cache_config IS '响应缓存配置 JSON：enabled/ttlSeconds/keyParams/includePageable';
COMMENT ON COLUMN flow_api_info.security_config IS '入站防护 JSON：authMode/antiReplay/rateLimit/ipAllowlist';
COMMENT ON COLUMN flow_api_info.privacy_config IS '出站隐私拦截 JSON：enabled/fieldSuffix/extraFields/mask';
COMMENT ON COLUMN flow_api_info.view_export_config IS '数据查看与导出 JSON：columns/sheetName/maxExportRows';
COMMENT ON COLUMN flow_api_info.create_time IS '创建时间，自动记录为当前时间';
COMMENT ON COLUMN flow_api_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_api_info_directory_id ON flow_api_info (directory_id);

-- >>> flow_asset_version.sql
-- Table: flow_asset_version
-- 资产发布历史版本
CREATE TABLE IF NOT EXISTS flow_asset_version (
  id varchar(32) NOT NULL,
  biz_type varchar(16) NOT NULL,
  asset_id varchar(32) NOT NULL,
  version_no integer NOT NULL,
  snapshot text NOT NULL,
  source varchar(16) NOT NULL DEFAULT 'publish',
  remark varchar(255),
  publisher varchar(64),
  publish_time timestamp NOT NULL,
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_asset_version_biz_type_asset_id_version_no UNIQUE (biz_type, asset_id, version_no)
);
COMMENT ON TABLE flow_asset_version IS '资产发布历史版本';
COMMENT ON COLUMN flow_asset_version.id IS '雪花ID';
COMMENT ON COLUMN flow_asset_version.biz_type IS '资产类型：api / task / service';
COMMENT ON COLUMN flow_asset_version.asset_id IS '资产ID';
COMMENT ON COLUMN flow_asset_version.version_no IS '同资产内递增版本号';
COMMENT ON COLUMN flow_asset_version.snapshot IS '发布快照 JSON（与各模块 published_snapshot 同构）';
COMMENT ON COLUMN flow_asset_version.source IS '来源：publish / rollback';
COMMENT ON COLUMN flow_asset_version.remark IS '备注';
COMMENT ON COLUMN flow_asset_version.publisher IS '发布人';
COMMENT ON COLUMN flow_asset_version.publish_time IS '发布时间';
COMMENT ON COLUMN flow_asset_version.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_asset_version_biz_type_asset_id_publish_time ON flow_asset_version (biz_type, asset_id, publish_time);

-- >>> flow_datasource.sql
-- Table: flow_datasource
-- 动态数据源配置表
CREATE TABLE IF NOT EXISTS flow_datasource (
  id varchar(64) NOT NULL,
  code varchar(50),
  name varchar(100) NOT NULL,
  db_type varchar(20) NOT NULL,
  driver_class_name varchar(200) NOT NULL,
  url varchar(500) NOT NULL,
  username varchar(100) NOT NULL,
  password varchar(100) NOT NULL,
  initial_size integer DEFAULT 5,
  min_idle integer DEFAULT 5,
  max_active integer DEFAULT 20,
  status smallint DEFAULT 1,
  wall_config text,
  is_system smallint NOT NULL DEFAULT 0,
  health_status varchar(20) NOT NULL DEFAULT 'UNKNOWN',
  error_count integer NOT NULL DEFAULT 0,
  last_error_msg text,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_datasource_name UNIQUE (name),
  CONSTRAINT uk_flow_datasource_code UNIQUE (code)
);
COMMENT ON TABLE flow_datasource IS '动态数据源配置表';
COMMENT ON COLUMN flow_datasource.id IS '主键ID';
COMMENT ON COLUMN flow_datasource.code IS '数据源全局唯一编码，用于跨环境关联';
COMMENT ON COLUMN flow_datasource.name IS '数据源名称';
COMMENT ON COLUMN flow_datasource.db_type IS '数据库类型(mysql/postgresql/highgo)';
COMMENT ON COLUMN flow_datasource.driver_class_name IS '驱动类名';
COMMENT ON COLUMN flow_datasource.url IS 'JDBC URL';
COMMENT ON COLUMN flow_datasource.username IS '用户名';
COMMENT ON COLUMN flow_datasource.password IS '密码';
COMMENT ON COLUMN flow_datasource.initial_size IS '初始连接数';
COMMENT ON COLUMN flow_datasource.min_idle IS '最小空闲连接';
COMMENT ON COLUMN flow_datasource.max_active IS '最大活动连接';
COMMENT ON COLUMN flow_datasource.status IS '状态(0-停用,1-启用)';
COMMENT ON COLUMN flow_datasource.wall_config IS 'SQL安全墙JSON(DataSourceWallConfig)';
COMMENT ON COLUMN flow_datasource.is_system IS '系统数据源(1=不可删改连接，如[DEFAULT])';
COMMENT ON COLUMN flow_datasource.health_status IS '连接健康度：HEALTHY-健康, UNHEALTHY-异常, UNKNOWN-未知';
COMMENT ON COLUMN flow_datasource.error_count IS '连续连接失败次数';
COMMENT ON COLUMN flow_datasource.last_error_msg IS '最后一次连接失败的异常堆栈/简述';
COMMENT ON COLUMN flow_datasource.create_time IS '创建时间';
COMMENT ON COLUMN flow_datasource.update_time IS '更新时间';

-- >>> flow_directory.sql
-- Table: flow_directory
-- 全局目录表
CREATE TABLE IF NOT EXISTS flow_directory (
  id varchar(32) NOT NULL,
  parent_id varchar(32),
  name varchar(128) NOT NULL,
  biz_type varchar(32),
  sort integer DEFAULT 0,
  path_prefix varchar(256),
  security_config text,
  privacy_config text,
  remark varchar(512),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_directory IS '全局目录表';
COMMENT ON COLUMN flow_directory.id IS '雪花ID';
COMMENT ON COLUMN flow_directory.parent_id IS '父节点ID，NULL 表示根节点';
COMMENT ON COLUMN flow_directory.name IS '目录名称';
COMMENT ON COLUMN flow_directory.biz_type IS '业务域：api/task/service/model/page，空=共用';
COMMENT ON COLUMN flow_directory.sort IS '排序（升序）';
COMMENT ON COLUMN flow_directory.path_prefix IS 'URL路径前缀，可空；新建接口默认继承';
COMMENT ON COLUMN flow_directory.security_config IS '目录级入站防护JSON，结构同ApiSecurityConfig';
COMMENT ON COLUMN flow_directory.privacy_config IS '目录级出站隐私JSON，结构同ApiPrivacyConfig';
COMMENT ON COLUMN flow_directory.remark IS '备注';
COMMENT ON COLUMN flow_directory.create_time IS '创建时间';
COMMENT ON COLUMN flow_directory.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_directory_biz_type ON flow_directory (biz_type);
CREATE INDEX IF NOT EXISTS idx_flow_directory_parent_id ON flow_directory (parent_id);

-- >>> flow_env.sql
-- Table: flow_env
-- 发布逻辑环境
CREATE TABLE IF NOT EXISTS flow_env (
  id varchar(32) NOT NULL,
  code varchar(32) NOT NULL,
  name varchar(100) NOT NULL,
  require_suite_pass smallint NOT NULL DEFAULT 0,
  pass_ttl_hours integer NOT NULL DEFAULT 24,
  enabled smallint NOT NULL DEFAULT 1,
  sort_order integer NOT NULL DEFAULT 0,
  remark varchar(512),
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_env_code UNIQUE (code)
);
COMMENT ON TABLE flow_env IS '发布逻辑环境';
COMMENT ON COLUMN flow_env.id IS '主键（固定码如 env_dev）';
COMMENT ON COLUMN flow_env.code IS 'DEV|STAGING|PROD';
COMMENT ON COLUMN flow_env.name IS '显示名';
COMMENT ON COLUMN flow_env.require_suite_pass IS '1=发布前需回归通过';
COMMENT ON COLUMN flow_env.pass_ttl_hours IS '通过结果有效小时数';
COMMENT ON COLUMN flow_env.enabled IS '启用：0=否, 1=是';
COMMENT ON COLUMN flow_env.sort_order IS '排序（升序）';
COMMENT ON COLUMN flow_env.remark IS '备注';
COMMENT ON COLUMN flow_env.create_time IS '创建时间';
COMMENT ON COLUMN flow_env.update_time IS '更新时间';

-- >>> flow_log_audit.sql
-- Table: flow_log_audit
-- 配置变更审计
CREATE TABLE IF NOT EXISTS flow_log_audit (
  id varchar(64) NOT NULL,
  action varchar(64) NOT NULL,
  operator varchar(100),
  target_type varchar(64),
  target_id varchar(64),
  detail varchar(1024),
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_audit IS '配置变更审计';
COMMENT ON COLUMN flow_log_audit.action IS 'API_PUBLISH|SYS_CONFIG_UPDATE|OPEN_SECRET_ROTATE';
COMMENT ON COLUMN flow_log_audit.operator IS '操作人';
COMMENT ON COLUMN flow_log_audit.target_type IS 'API|SYS_CONFIG|OPEN_CREDENTIAL';
COMMENT ON COLUMN flow_log_audit.detail IS 'JSON 摘要，不含密钥';
CREATE INDEX IF NOT EXISTS idx_flow_log_audit_action_create_time ON flow_log_audit (action, create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_audit_operator ON flow_log_audit (operator);

-- >>> flow_log_execution.sql
-- Table: flow_log_execution
-- API执行日志表
CREATE TABLE IF NOT EXISTS flow_log_execution (
  id varchar(32) NOT NULL,
  api_id varchar(32),
  api_name varchar(128),
  url varchar(255),
  method varchar(16),
  request_params text,
  response_body text,
  status varchar(16),
  error_msg text,
  cost_time_ms bigint,
  trace_data text,
  service_type varchar(32),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_execution IS 'API执行日志表';
COMMENT ON COLUMN flow_log_execution.id IS '主键ID';
COMMENT ON COLUMN flow_log_execution.api_id IS 'API ID';
COMMENT ON COLUMN flow_log_execution.api_name IS 'API名称';
COMMENT ON COLUMN flow_log_execution.url IS '请求URL';
COMMENT ON COLUMN flow_log_execution.method IS '请求方法';
COMMENT ON COLUMN flow_log_execution.request_params IS '请求参数快照(JSON)';
COMMENT ON COLUMN flow_log_execution.response_body IS '返回结果快照(JSON)';
COMMENT ON COLUMN flow_log_execution.status IS '执行状态: SUCCESS/ERROR';
COMMENT ON COLUMN flow_log_execution.cost_time_ms IS '耗时(毫秒)';
COMMENT ON COLUMN flow_log_execution.trace_data IS '完整追踪快照(如有)';
COMMENT ON COLUMN flow_log_execution.service_type IS '接口类型: FLOW/DB/JSON/STRING';
COMMENT ON COLUMN flow_log_execution.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_execution_api_id ON flow_log_execution (api_id);
CREATE INDEX IF NOT EXISTS idx_flow_log_execution_create_time ON flow_log_execution (create_time);

-- >>> flow_log_login.sql
-- Table: flow_log_login
-- 登录日志表
CREATE TABLE IF NOT EXISTS flow_log_login (
  id varchar(64) NOT NULL,
  account varchar(100) NOT NULL,
  ip varchar(64),
  region varchar(255),
  user_agent varchar(512),
  status smallint NOT NULL DEFAULT 0,
  msg varchar(255),
  duration bigint,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_login IS '登录日志表';
COMMENT ON COLUMN flow_log_login.id IS '主键';
COMMENT ON COLUMN flow_log_login.account IS '登录账号';
COMMENT ON COLUMN flow_log_login.ip IS '客户端 IP';
COMMENT ON COLUMN flow_log_login.region IS 'IP 归属地区';
COMMENT ON COLUMN flow_log_login.user_agent IS '浏览器 User-Agent';
COMMENT ON COLUMN flow_log_login.status IS '登录状态（1: 成功, 0: 失败）';
COMMENT ON COLUMN flow_log_login.msg IS '登录结果信息';
COMMENT ON COLUMN flow_log_login.duration IS '登录耗时（毫秒）';
COMMENT ON COLUMN flow_log_login.create_time IS '登录时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_login_account ON flow_log_login (account);
CREATE INDEX IF NOT EXISTS idx_flow_log_login_create_time ON flow_log_login (create_time);

-- >>> flow_log_open_call.sql
-- Table: flow_log_open_call
-- 开放平台入站调用摘要
CREATE TABLE IF NOT EXISTS flow_log_open_call (
  id varchar(32) NOT NULL,
  platform_id varchar(32),
  app_key varchar(64),
  api_id varchar(32),
  method varchar(16),
  path varchar(512),
  status integer,
  cost_ms bigint,
  error_code varchar(64),
  request_id varchar(64),
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_open_call IS '开放平台入站调用摘要';
COMMENT ON COLUMN flow_log_open_call.id IS '雪花ID';
COMMENT ON COLUMN flow_log_open_call.platform_id IS '开放平台ID';
COMMENT ON COLUMN flow_log_open_call.app_key IS '调用方 AppKey';
COMMENT ON COLUMN flow_log_open_call.api_id IS '命中的 API ID';
COMMENT ON COLUMN flow_log_open_call.method IS 'HTTP 方法';
COMMENT ON COLUMN flow_log_open_call.path IS '真实发布路径';
COMMENT ON COLUMN flow_log_open_call.status IS 'HTTP 状态';
COMMENT ON COLUMN flow_log_open_call.cost_ms IS '耗时毫秒';
COMMENT ON COLUMN flow_log_open_call.error_code IS '业务/鉴权错误码';
COMMENT ON COLUMN flow_log_open_call.request_id IS '请求追踪ID';
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_app_key ON flow_log_open_call (app_key);
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_create_time ON flow_log_open_call (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_platform_id_create_time ON flow_log_open_call (platform_id, create_time);

-- >>> flow_log_service.sql
-- Table: flow_log_service
-- 内部服务编排执行日志
CREATE TABLE IF NOT EXISTS flow_log_service (
  id varchar(32) NOT NULL,
  service_id varchar(32) NOT NULL,
  service_name varchar(128),
  trigger_type varchar(16) NOT NULL DEFAULT 'MANUAL',
  status varchar(16) NOT NULL,
  cost_time_ms bigint,
  error_msg text,
  trace_data text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_service IS '内部服务编排执行日志';
COMMENT ON COLUMN flow_log_service.id IS '雪花ID';
COMMENT ON COLUMN flow_log_service.service_id IS '关联服务ID';
COMMENT ON COLUMN flow_log_service.service_name IS '服务名称（冗余）';
COMMENT ON COLUMN flow_log_service.trigger_type IS '触发类型：MANUAL=手动, CALL=流程内调用, DEBUG=调试';
COMMENT ON COLUMN flow_log_service.status IS '执行状态：SUCCESS / FAILED / RUNNING';
COMMENT ON COLUMN flow_log_service.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_log_service.error_msg IS '失败信息';
COMMENT ON COLUMN flow_log_service.trace_data IS 'FlowTrace JSON 快照（logEnabled=true 时记录）';
COMMENT ON COLUMN flow_log_service.create_time IS '执行开始时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_service_create_time ON flow_log_service (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_service_service_id ON flow_log_service (service_id);
CREATE INDEX IF NOT EXISTS idx_flow_log_service_status ON flow_log_service (status);

-- >>> flow_log_task.sql
-- Table: flow_log_task
-- 定时任务执行日志
CREATE TABLE IF NOT EXISTS flow_log_task (
  id varchar(32) NOT NULL,
  task_id varchar(32) NOT NULL,
  task_name varchar(128),
  trigger_type varchar(16) NOT NULL DEFAULT 'CRON',
  status varchar(16) NOT NULL,
  cost_time_ms bigint,
  error_msg text,
  trace_data text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_task IS '定时任务执行日志';
COMMENT ON COLUMN flow_log_task.id IS '雪花ID';
COMMENT ON COLUMN flow_log_task.task_id IS '关联任务ID';
COMMENT ON COLUMN flow_log_task.task_name IS '任务名称（冗余）';
COMMENT ON COLUMN flow_log_task.trigger_type IS '触发类型：CRON=定时, MANUAL=手动';
COMMENT ON COLUMN flow_log_task.status IS '执行状态：SUCCESS / FAILED / RUNNING';
COMMENT ON COLUMN flow_log_task.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_log_task.error_msg IS '失败信息';
COMMENT ON COLUMN flow_log_task.trace_data IS 'FlowTrace JSON 快照（logEnabled=true 时记录）';
COMMENT ON COLUMN flow_log_task.create_time IS '执行开始时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_task_create_time ON flow_log_task (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_task_status ON flow_log_task (status);
CREATE INDEX IF NOT EXISTS idx_flow_log_task_task_id ON flow_log_task (task_id);

-- >>> flow_log_third.sql
-- Table: flow_log_third
-- 第三方接口调用日志
CREATE TABLE IF NOT EXISTS flow_log_third (
  id varchar(64) NOT NULL,
  api_type varchar(50),
  source varchar(16),
  source_ref varchar(64),
  source_name varchar(128),
  request_url varchar(512),
  request_method varchar(10),
  request_params text,
  request_headers text,
  response_status integer,
  response_body text,
  elapsed_time bigint,
  is_success smallint,
  error_message varchar(1000),
  curl text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_third IS '第三方接口调用日志';
COMMENT ON COLUMN flow_log_third.id IS '主键';
COMMENT ON COLUMN flow_log_third.api_type IS '接口标识';
COMMENT ON COLUMN flow_log_third.source IS '调用来源：API/TASK/DEBUG/OTHER';
COMMENT ON COLUMN flow_log_third.source_ref IS '来源关联ID（apiId/taskId）';
COMMENT ON COLUMN flow_log_third.source_name IS '来源名称（接口名/任务名）';
COMMENT ON COLUMN flow_log_third.request_url IS '请求URL';
COMMENT ON COLUMN flow_log_third.request_method IS '请求方法';
COMMENT ON COLUMN flow_log_third.request_params IS '请求参数';
COMMENT ON COLUMN flow_log_third.request_headers IS '请求头';
COMMENT ON COLUMN flow_log_third.response_status IS '响应状态码';
COMMENT ON COLUMN flow_log_third.response_body IS '响应内容';
COMMENT ON COLUMN flow_log_third.elapsed_time IS '请求耗时(ms)';
COMMENT ON COLUMN flow_log_third.is_success IS '是否成功（0失败/1成功）';
COMMENT ON COLUMN flow_log_third.error_message IS '错误信息';
COMMENT ON COLUMN flow_log_third.curl IS 'Curl命令';
COMMENT ON COLUMN flow_log_third.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_third_api_type ON flow_log_third (api_type);
CREATE INDEX IF NOT EXISTS idx_flow_log_third_create_time ON flow_log_third (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_third_source ON flow_log_third (source);

-- >>> flow_metrics_meta.sql
-- Table: flow_metrics_meta
-- 资产运行计量元数据
CREATE TABLE IF NOT EXISTS flow_metrics_meta (
  id varchar(32) NOT NULL,
  asset_type varchar(16) NOT NULL,
  asset_id varchar(32) NOT NULL,
  last_success_at bigint,
  last_fail_at bigint,
  consec_fail bigint NOT NULL DEFAULT 0,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_metrics_meta_asset_type_asset_id UNIQUE (asset_type, asset_id)
);
COMMENT ON TABLE flow_metrics_meta IS '资产运行计量元数据';
COMMENT ON COLUMN flow_metrics_meta.id IS '雪花ID';
COMMENT ON COLUMN flow_metrics_meta.asset_type IS 'API / TASK / MQ_TASK / SERVICE / PLATFORM / SYSTEM';
COMMENT ON COLUMN flow_metrics_meta.asset_id IS '资产ID';
COMMENT ON COLUMN flow_metrics_meta.last_success_at IS '最近成功 epoch ms';
COMMENT ON COLUMN flow_metrics_meta.last_fail_at IS '最近业务失败 epoch ms';
COMMENT ON COLUMN flow_metrics_meta.consec_fail IS '连续业务失败次数';
COMMENT ON COLUMN flow_metrics_meta.update_time IS '最后更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_metrics_meta_asset_type ON flow_metrics_meta (asset_type);

-- >>> flow_metrics_minute.sql
-- Table: flow_metrics_minute
-- 资产运行计量分钟汇总
CREATE TABLE IF NOT EXISTS flow_metrics_minute (
  id varchar(32) NOT NULL,
  asset_type varchar(16) NOT NULL,
  asset_id varchar(32) NOT NULL,
  trigger_type varchar(16) NOT NULL DEFAULT '_',
  bucket_start timestamp NOT NULL,
  success_cnt bigint NOT NULL DEFAULT 0,
  fail_cnt bigint NOT NULL DEFAULT 0,
  auth_fail_cnt bigint NOT NULL DEFAULT 0,
  skipped_cnt bigint NOT NULL DEFAULT 0,
  sum_cost_ms bigint NOT NULL DEFAULT 0,
  latency_count bigint NOT NULL DEFAULT 0,
  hist_json varchar(1024),
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_metrics_minute_atype_aid_ttype_bstart UNIQUE (asset_type, asset_id, trigger_type, bucket_start)
);
COMMENT ON TABLE flow_metrics_minute IS '资产运行计量分钟汇总';
COMMENT ON COLUMN flow_metrics_minute.id IS '雪花ID';
COMMENT ON COLUMN flow_metrics_minute.asset_type IS 'API / TASK / MQ_TASK / SERVICE / PLATFORM / SYSTEM';
COMMENT ON COLUMN flow_metrics_minute.asset_id IS '资产ID';
COMMENT ON COLUMN flow_metrics_minute.trigger_type IS '触发类型；API 固定为 _';
COMMENT ON COLUMN flow_metrics_minute.bucket_start IS '分钟桶起点（整分）';
COMMENT ON COLUMN flow_metrics_minute.success_cnt IS '成功次数';
COMMENT ON COLUMN flow_metrics_minute.fail_cnt IS '失败次数';
COMMENT ON COLUMN flow_metrics_minute.auth_fail_cnt IS '鉴权失败次数（如开放平台 401/403）';
COMMENT ON COLUMN flow_metrics_minute.skipped_cnt IS '跳过次数';
COMMENT ON COLUMN flow_metrics_minute.sum_cost_ms IS '耗时合计（毫秒）';
COMMENT ON COLUMN flow_metrics_minute.latency_count IS '参与延迟统计的次数';
COMMENT ON COLUMN flow_metrics_minute.hist_json IS '直方图桶计数 JSON 数组';
COMMENT ON COLUMN flow_metrics_minute.update_time IS '最后更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_metrics_minute_bucket_start ON flow_metrics_minute (bucket_start);
CREATE INDEX IF NOT EXISTS idx_flow_metrics_minute_asset_type_bucket_start ON flow_metrics_minute (asset_type, bucket_start);

-- >>> flow_model_directory.sql
-- Table: flow_model_directory
-- 数据模型目录表
CREATE TABLE IF NOT EXISTS flow_model_directory (
  id varchar(32) NOT NULL,
  parent_id varchar(32),
  name varchar(128) NOT NULL,
  sort integer DEFAULT 0,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_model_directory IS '数据模型目录表';
COMMENT ON COLUMN flow_model_directory.id IS '主键（雪花ID）';
COMMENT ON COLUMN flow_model_directory.parent_id IS '父节点ID，NULL 表示根节点';
COMMENT ON COLUMN flow_model_directory.name IS '目录名称';
COMMENT ON COLUMN flow_model_directory.sort IS '排序（升序）';
COMMENT ON COLUMN flow_model_directory.create_time IS '创建时间';
COMMENT ON COLUMN flow_model_directory.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_model_directory_parent_id ON flow_model_directory (parent_id);

-- >>> flow_model_info.sql
-- Table: flow_model_info
-- 数据模型信息表
CREATE TABLE IF NOT EXISTS flow_model_info (
  id varchar(32) NOT NULL,
  directory_id varchar(32),
  name varchar(256) NOT NULL,
  table_name varchar(256) NOT NULL,
  fields_schema text,
  status smallint DEFAULT 0,
  datasource varchar(50),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_model_info_table_name UNIQUE (table_name)
);
COMMENT ON TABLE flow_model_info IS '数据模型信息表';
COMMENT ON COLUMN flow_model_info.id IS '主键（雪花ID）';
COMMENT ON COLUMN flow_model_info.directory_id IS '关联全局目录树';
COMMENT ON COLUMN flow_model_info.name IS '模型中文名，如：用户信息';
COMMENT ON COLUMN flow_model_info.table_name IS '底层物理表名，如：t_user';
COMMENT ON COLUMN flow_model_info.fields_schema IS '核心元数据 JSON 数组（包含字段名、类型、UI配置等）';
COMMENT ON COLUMN flow_model_info.status IS '状态：0=停用，1=启用';
COMMENT ON COLUMN flow_model_info.datasource IS '关联的动态数据源 code';
COMMENT ON COLUMN flow_model_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_model_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_model_info_directory_id ON flow_model_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_model_info_status ON flow_model_info (status);

-- >>> flow_mq_connection.sql
-- Table: flow_mq_connection
-- MQ 连接配置（RabbitMQ / Kafka）
CREATE TABLE IF NOT EXISTS flow_mq_connection (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  mq_type varchar(32) NOT NULL,
  servers varchar(512) NOT NULL,
  virtual_host varchar(128),
  username varchar(128),
  password varchar(512),
  enabled boolean NOT NULL DEFAULT true,
  health_status varchar(32),
  last_error_msg varchar(1024),
  last_test_time timestamp,
  info varchar(512),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_mq_connection IS 'MQ 连接配置';
COMMENT ON COLUMN flow_mq_connection.id IS '雪花ID';
COMMENT ON COLUMN flow_mq_connection.code IS '连接编码（未删除记录内唯一），流程 DSL / MQ 任务通过 code 引用';
COMMENT ON COLUMN flow_mq_connection.name IS '连接名称';
COMMENT ON COLUMN flow_mq_connection.mq_type IS 'MQ 类型：RABBITMQ / KAFKA';
COMMENT ON COLUMN flow_mq_connection.servers IS '服务器地址 host:port，多个逗号分隔（Kafka 即 bootstrap.servers）';
COMMENT ON COLUMN flow_mq_connection.virtual_host IS '虚拟主机（仅 RabbitMQ，默认 /）';
COMMENT ON COLUMN flow_mq_connection.username IS '用户名（可空；Kafka 有值时启用 SASL/PLAIN）';
COMMENT ON COLUMN flow_mq_connection.password IS '密码（AES 密文存储）';
COMMENT ON COLUMN flow_mq_connection.enabled IS '启用状态：0=停用, 1=启用';
COMMENT ON COLUMN flow_mq_connection.health_status IS '健康状态：HEALTHY / UNHEALTHY / UNKNOWN';
COMMENT ON COLUMN flow_mq_connection.last_error_msg IS '最近一次连接测试错误信息';
COMMENT ON COLUMN flow_mq_connection.last_test_time IS '最近一次连接测试时间';
COMMENT ON COLUMN flow_mq_connection.info IS '备注描述';
COMMENT ON COLUMN flow_mq_connection.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_mq_connection.create_time IS '创建时间';
COMMENT ON COLUMN flow_mq_connection.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_mq_connection_code ON flow_mq_connection (code);
CREATE INDEX IF NOT EXISTS idx_flow_mq_connection_enabled ON flow_mq_connection (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_mq_connection_create_time ON flow_mq_connection (create_time);

-- >>> flow_mq_task_info.sql
-- Table: flow_mq_task_info
-- MQ 任务定义（mqTrigger 入口流程资产）
CREATE TABLE IF NOT EXISTS flow_mq_task_info (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  directory_id varchar(32),
  connection_code varchar(64) NOT NULL,
  topic varchar(255) NOT NULL,
  consumer_group varchar(128),
  concurrency integer NOT NULL DEFAULT 1,
  enabled boolean NOT NULL DEFAULT true,
  log_enabled boolean NOT NULL DEFAULT true,
  log_mode varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
  log_payload_mode varchar(16) DEFAULT 'SYSTEM_DEFAULT',
  log_retention_days integer,
  retry_max integer DEFAULT 0,
  retry_backoff_ms integer DEFAULT 1000,
  dead_letter_topic varchar(255) DEFAULT NULL,
  dsl_content text,
  publish_status smallint NOT NULL DEFAULT 0,
  published_snapshot text,
  publish_time timestamp,
  info varchar(512),
  tags varchar(255),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_mq_task_info IS 'MQ 任务定义';
COMMENT ON COLUMN flow_mq_task_info.id IS '雪花ID';
COMMENT ON COLUMN flow_mq_task_info.name IS '任务名称';
COMMENT ON COLUMN flow_mq_task_info.directory_id IS '关联目录ID（复用全局目录树）';
COMMENT ON COLUMN flow_mq_task_info.connection_code IS '绑定 MQ 连接编码（flow_mq_connection.code）';
COMMENT ON COLUMN flow_mq_task_info.topic IS '订阅 topic / 队列名';
COMMENT ON COLUMN flow_mq_task_info.consumer_group IS '消费组（Kafka group.id；Rabbit 忽略）';
COMMENT ON COLUMN flow_mq_task_info.concurrency IS '消费并发数';
COMMENT ON COLUMN flow_mq_task_info.enabled IS '启用状态：0=停用, 1=启用';
COMMENT ON COLUMN flow_mq_task_info.log_enabled IS '是否记录执行日志';
COMMENT ON COLUMN flow_mq_task_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';
COMMENT ON COLUMN flow_mq_task_info.log_payload_mode IS '原始报文落库策略：SYSTEM_DEFAULT/FULL/MASK/OFF';
COMMENT ON COLUMN flow_mq_task_info.log_retention_days IS '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数';
COMMENT ON COLUMN flow_mq_task_info.retry_max IS '失败重试次数（0=不重试）';
COMMENT ON COLUMN flow_mq_task_info.retry_backoff_ms IS '重试间隔毫秒';
COMMENT ON COLUMN flow_mq_task_info.dead_letter_topic IS '最终失败时转发的死信 topic/队列';
COMMENT ON COLUMN flow_mq_task_info.dsl_content IS '流程定义 DSL JSON（草稿）';
COMMENT ON COLUMN flow_mq_task_info.publish_status IS '发布状态：0=未发布，1=已发布';
COMMENT ON COLUMN flow_mq_task_info.published_snapshot IS '发布快照 JSON：dslContent';
COMMENT ON COLUMN flow_mq_task_info.publish_time IS '最近发布时间';
COMMENT ON COLUMN flow_mq_task_info.info IS '任务描述';
COMMENT ON COLUMN flow_mq_task_info.tags IS '标签，英文逗号分隔';
COMMENT ON COLUMN flow_mq_task_info.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_mq_task_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_mq_task_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_create_time ON flow_mq_task_info (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_directory_id ON flow_mq_task_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_enabled ON flow_mq_task_info (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_publish_status ON flow_mq_task_info (publish_status);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_connection_code ON flow_mq_task_info (connection_code);

-- >>> flow_mq_task_log.sql
-- Table: flow_mq_task_log
-- MQ 任务执行日志
CREATE TABLE IF NOT EXISTS flow_mq_task_log (
  id varchar(32) NOT NULL,
  task_id varchar(32) NOT NULL,
  task_name varchar(128),
  topic varchar(255),
  message_id varchar(255),
  trigger_type varchar(16) NOT NULL DEFAULT 'MQ',
  status varchar(16) NOT NULL,
  cost_time_ms bigint,
  error_msg text,
  message_body text,
  message_headers text,
  trace_data text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_mq_task_log IS 'MQ 任务执行日志';
COMMENT ON COLUMN flow_mq_task_log.id IS '雪花ID';
COMMENT ON COLUMN flow_mq_task_log.task_id IS '关联 MQ 任务ID';
COMMENT ON COLUMN flow_mq_task_log.task_name IS '任务名称（冗余）';
COMMENT ON COLUMN flow_mq_task_log.topic IS '消息 topic / 队列名';
COMMENT ON COLUMN flow_mq_task_log.message_id IS '消息ID（幂等去重键）';
COMMENT ON COLUMN flow_mq_task_log.trigger_type IS '触发类型：MQ=消息触发, MANUAL=手动';
COMMENT ON COLUMN flow_mq_task_log.status IS '执行状态：SUCCESS / FAILED / SKIPPED / RUNNING';
COMMENT ON COLUMN flow_mq_task_log.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_mq_task_log.error_msg IS '失败信息';
COMMENT ON COLUMN flow_mq_task_log.message_body IS '原始消息体（JSON 解析前的字符串，支持超限截断）';
COMMENT ON COLUMN flow_mq_task_log.message_headers IS '消息头 JSON 字典';
COMMENT ON COLUMN flow_mq_task_log.trace_data IS 'FlowTrace JSON 快照（logMode=ALL 时记录）';
COMMENT ON COLUMN flow_mq_task_log.create_time IS '执行开始时间';
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_create_time ON flow_mq_task_log (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_status ON flow_mq_task_log (status);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_task_id ON flow_mq_task_log (task_id);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_message_id ON flow_mq_task_log (message_id);

-- >>> flow_open_api_grant.sql
-- Table: flow_open_api_grant
-- 开放平台接口授权
CREATE TABLE IF NOT EXISTS flow_open_api_grant (
  id varchar(32) NOT NULL,
  platform_id varchar(32) NOT NULL,
  api_id varchar(32) NOT NULL,
  allow_methods varchar(64),
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_open_api_grant_platform_id_api_id UNIQUE (platform_id, api_id)
);
COMMENT ON TABLE flow_open_api_grant IS '开放平台接口授权';
COMMENT ON COLUMN flow_open_api_grant.allow_methods IS '空=跟随接口方法';
CREATE INDEX IF NOT EXISTS idx_flow_open_api_grant_api_id ON flow_open_api_grant (api_id);

-- >>> flow_open_credential.sql
-- Table: flow_open_credential
-- 开放平台凭证
CREATE TABLE IF NOT EXISTS flow_open_credential (
  id varchar(32) NOT NULL,
  platform_id varchar(32) NOT NULL,
  app_key varchar(64) NOT NULL,
  app_secret_enc varchar(512) NOT NULL,
  secret_hint varchar(16),
  status smallint NOT NULL DEFAULT 1,
  rotated_from_id varchar(32),
  expire_at timestamp,
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_open_credential_app_key UNIQUE (app_key)
);
COMMENT ON TABLE flow_open_credential IS '开放平台凭证';
COMMENT ON COLUMN flow_open_credential.app_secret_enc IS 'AES加密后的secret';
COMMENT ON COLUMN flow_open_credential.secret_hint IS '末4位提示';
COMMENT ON COLUMN flow_open_credential.status IS '0停用 1启用 2已轮换废弃';
COMMENT ON COLUMN flow_open_credential.rotated_from_id IS '轮换来源凭证';
CREATE INDEX IF NOT EXISTS idx_flow_open_credential_platform_id ON flow_open_credential (platform_id);

-- >>> flow_open_platform.sql
-- Table: flow_open_platform
-- 第三方开放平台
CREATE TABLE IF NOT EXISTS flow_open_platform (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  status smallint NOT NULL DEFAULT 1,
  contact varchar(128),
  remark varchar(512),
  ip_allowlist varchar(1024),
  expire_at timestamp,
  open_call_log_enabled smallint DEFAULT 1,
  rate_limit_qps integer,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_open_platform_code UNIQUE (code)
);
COMMENT ON TABLE flow_open_platform IS '第三方开放平台';
COMMENT ON COLUMN flow_open_platform.id IS '雪花ID';
COMMENT ON COLUMN flow_open_platform.code IS '唯一编码';
COMMENT ON COLUMN flow_open_platform.name IS '平台名称';
COMMENT ON COLUMN flow_open_platform.status IS '0停用 1启用';
COMMENT ON COLUMN flow_open_platform.contact IS '联系人';
COMMENT ON COLUMN flow_open_platform.remark IS '备注';
COMMENT ON COLUMN flow_open_platform.ip_allowlist IS 'IP白名单JSON数组，空=不限';
COMMENT ON COLUMN flow_open_platform.expire_at IS '平台到期时间';
COMMENT ON COLUMN flow_open_platform.open_call_log_enabled IS '是否记录入站摘要日志 0关1开';
COMMENT ON COLUMN flow_open_platform.rate_limit_qps IS '平台级 QPS 上限，空=不限';
CREATE INDEX IF NOT EXISTS idx_flow_open_platform_status ON flow_open_platform (status);

-- >>> flow_oss_connection.sql
-- Table: flow_oss_connection
-- MinIO / S3 兼容对象存储连接配置
CREATE TABLE IF NOT EXISTS flow_oss_connection (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  endpoint varchar(512) NOT NULL,
  access_key varchar(128),
  secret_key varchar(512),
  region varchar(64),
  path_style boolean NOT NULL DEFAULT true,
  public_bucket varchar(128),
  private_bucket varchar(128),
  public_base_url varchar(512),
  key_prefix varchar(256),
  public_access_mode varchar(32) DEFAULT 'NGINX_PROXY',
  private_download_mode varchar(16) NOT NULL DEFAULT 'STREAM',
  presign_expire_seconds integer NOT NULL DEFAULT 300,
  enabled boolean NOT NULL DEFAULT true,
  health_status varchar(32),
  last_error_msg varchar(1024),
  last_test_time timestamp,
  info varchar(512),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_connection IS 'OSS 连接配置';
COMMENT ON COLUMN flow_oss_connection.id IS '雪花ID';
COMMENT ON COLUMN flow_oss_connection.code IS '连接编码（未删除记录内唯一）';
COMMENT ON COLUMN flow_oss_connection.name IS '连接名称';
COMMENT ON COLUMN flow_oss_connection.endpoint IS 'MinIO endpoint，如 http://minio:9000';
COMMENT ON COLUMN flow_oss_connection.access_key IS 'Access Key';
COMMENT ON COLUMN flow_oss_connection.secret_key IS 'Secret Key（AES 密文）';
COMMENT ON COLUMN flow_oss_connection.region IS '区域（可空）';
COMMENT ON COLUMN flow_oss_connection.path_style IS '是否 path-style 访问';
COMMENT ON COLUMN flow_oss_connection.public_bucket IS '公有桶名';
COMMENT ON COLUMN flow_oss_connection.private_bucket IS '私有桶名';
COMMENT ON COLUMN flow_oss_connection.public_base_url IS '公有访问前缀（Nginx 对外 URL）';
COMMENT ON COLUMN flow_oss_connection.key_prefix IS '对象键强制前缀';
COMMENT ON COLUMN flow_oss_connection.public_access_mode IS 'ANON / NGINX_PROXY';
COMMENT ON COLUMN flow_oss_connection.private_download_mode IS '隐私下载：STREAM / PRESIGN';
COMMENT ON COLUMN flow_oss_connection.presign_expire_seconds IS '预签名有效期（秒）';
COMMENT ON COLUMN flow_oss_connection.enabled IS '启用状态';
COMMENT ON COLUMN flow_oss_connection.health_status IS 'HEALTHY / UNHEALTHY / UNKNOWN';
COMMENT ON COLUMN flow_oss_connection.last_error_msg IS '最近测试错误';
COMMENT ON COLUMN flow_oss_connection.last_test_time IS '最近测试时间';
COMMENT ON COLUMN flow_oss_connection.info IS '备注';
COMMENT ON COLUMN flow_oss_connection.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_oss_connection.create_time IS '创建时间';
COMMENT ON COLUMN flow_oss_connection.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_code ON flow_oss_connection (code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_enabled ON flow_oss_connection (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_create_time ON flow_oss_connection (create_time);

-- >>> flow_oss_download_log.sql
-- Table: flow_oss_download_log
-- OSS 隐私下载审计
CREATE TABLE IF NOT EXISTS flow_oss_download_log (
  id varchar(32) NOT NULL,
  object_id varchar(32),
  downloaded_by varchar(64),
  downloaded_by_name varchar(128),
  client_ip varchar(64),
  user_agent varchar(512),
  result varchar(16),
  deny_reason varchar(512),
  time_ms bigint,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_download_log IS 'OSS 隐私下载审计';
COMMENT ON COLUMN flow_oss_download_log.id IS '雪花ID';
COMMENT ON COLUMN flow_oss_download_log.object_id IS '台账 ID';
COMMENT ON COLUMN flow_oss_download_log.downloaded_by IS '下载人 userId';
COMMENT ON COLUMN flow_oss_download_log.downloaded_by_name IS '下载人展示名';
COMMENT ON COLUMN flow_oss_download_log.client_ip IS '客户端 IP';
COMMENT ON COLUMN flow_oss_download_log.user_agent IS 'User-Agent';
COMMENT ON COLUMN flow_oss_download_log.result IS 'SUCCESS / DENIED / NOT_FOUND / ERROR';
COMMENT ON COLUMN flow_oss_download_log.deny_reason IS '拒绝原因';
COMMENT ON COLUMN flow_oss_download_log.time_ms IS '耗时毫秒';
COMMENT ON COLUMN flow_oss_download_log.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_object_id ON flow_oss_download_log (object_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_create_time ON flow_oss_download_log (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_result ON flow_oss_download_log (result);

-- >>> flow_oss_object_ref.sql
-- Table: flow_oss_object_ref
-- 对象存储业务引用
CREATE TABLE IF NOT EXISTS flow_oss_object_ref (
  id varchar(32) NOT NULL,
  object_id varchar(32) NOT NULL,
  biz_type varchar(64) NOT NULL,
  biz_id varchar(128) NOT NULL,
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_oss_object_ref_object_id_biz_type_biz_id UNIQUE (object_id, biz_type, biz_id)
);
COMMENT ON TABLE flow_oss_object_ref IS '对象存储业务引用';
COMMENT ON COLUMN flow_oss_object_ref.id IS '雪花ID';
COMMENT ON COLUMN flow_oss_object_ref.object_id IS '台账ID';
COMMENT ON COLUMN flow_oss_object_ref.biz_type IS '业务类型';
COMMENT ON COLUMN flow_oss_object_ref.biz_id IS '业务单据ID';
COMMENT ON COLUMN flow_oss_object_ref.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_ref_object_id ON flow_oss_object_ref (object_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_ref_biz_type_biz_id ON flow_oss_object_ref (biz_type, biz_id);

-- >>> flow_oss_object.sql
-- Table: flow_oss_object
-- OSS 文件台账（元数据在库，字节在 MinIO）
CREATE TABLE IF NOT EXISTS flow_oss_object (
  id varchar(32) NOT NULL,
  profile_code varchar(64),
  connection_code varchar(64),
  bucket varchar(128),
  object_key varchar(1024),
  visibility varchar(16),
  public_path varchar(1024),
  original_name varchar(512),
  content_type varchar(128),
  extension varchar(32),
  size_bytes bigint,
  checksum_sha256 varchar(64),
  biz_meta text,
  uploaded_by varchar(64),
  uploaded_by_user_type varchar(32),
  uploaded_by_name varchar(128),
  dept_id varchar(64),
  status varchar(16) NOT NULL DEFAULT 'ACTIVE',
  expires_at timestamp,
  object_purged boolean NOT NULL DEFAULT false,
  thumb_status varchar(16) NOT NULL DEFAULT 'NONE',
  thumb_object_key varchar(1024),
  thumb_public_path varchar(1024),
  thumb_content_type varchar(128),
  thumb_size_bytes bigint,
  thumb_error varchar(512),
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_object IS 'OSS 文件台账';
COMMENT ON COLUMN flow_oss_object.id IS '雪花ID（隐私下载主键）';
COMMENT ON COLUMN flow_oss_object.profile_code IS '上传场景编码';
COMMENT ON COLUMN flow_oss_object.connection_code IS '连接编码';
COMMENT ON COLUMN flow_oss_object.bucket IS '桶名';
COMMENT ON COLUMN flow_oss_object.object_key IS '对象键';
COMMENT ON COLUMN flow_oss_object.visibility IS 'PUBLIC / PRIVATE';
COMMENT ON COLUMN flow_oss_object.public_path IS '公有相对路径';
COMMENT ON COLUMN flow_oss_object.original_name IS '原始文件名';
COMMENT ON COLUMN flow_oss_object.content_type IS 'Content-Type';
COMMENT ON COLUMN flow_oss_object.extension IS '扩展名';
COMMENT ON COLUMN flow_oss_object.size_bytes IS '文件大小';
COMMENT ON COLUMN flow_oss_object.checksum_sha256 IS 'SHA-256';
COMMENT ON COLUMN flow_oss_object.biz_meta IS '业务扩展 JSON';
COMMENT ON COLUMN flow_oss_object.uploaded_by IS '上传人 userId（各用户体系内主键，不带类型前缀）';
COMMENT ON COLUMN flow_oss_object.uploaded_by_user_type IS '上传人 userType：ADMIN / END_USER / OPEN_APP（宿主可扩展）';
COMMENT ON COLUMN flow_oss_object.uploaded_by_name IS '上传人展示名';
COMMENT ON COLUMN flow_oss_object.dept_id IS '部门 ID（数据权限）';
COMMENT ON COLUMN flow_oss_object.status IS 'ACTIVE / PENDING（预签名待确认）/ DELETED';
COMMENT ON COLUMN flow_oss_object.expires_at IS '临时文件过期时间，空=不过期';
COMMENT ON COLUMN flow_oss_object.object_purged IS 'MinIO 对象是否已物理删除';
COMMENT ON COLUMN flow_oss_object.thumb_status IS 'NONE / PENDING / READY / FAILED / SKIPPED';
COMMENT ON COLUMN flow_oss_object.thumb_object_key IS '缩略图对象键';
COMMENT ON COLUMN flow_oss_object.thumb_public_path IS '缩略图公有路径';
COMMENT ON COLUMN flow_oss_object.thumb_content_type IS '缩略图 Content-Type';
COMMENT ON COLUMN flow_oss_object.thumb_size_bytes IS '缩略图大小';
COMMENT ON COLUMN flow_oss_object.thumb_error IS '缩略图失败原因';
COMMENT ON COLUMN flow_oss_object.create_time IS '创建时间';
COMMENT ON COLUMN flow_oss_object.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_profile_code ON flow_oss_object (profile_code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_uploaded_by_uploaded_by_user_type
  ON flow_oss_object (uploaded_by, uploaded_by_user_type);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_dept_id ON flow_oss_object (dept_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_status ON flow_oss_object (status);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_create_time ON flow_oss_object (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_expires_at ON flow_oss_object (expires_at);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_status_object_purged ON flow_oss_object (status, object_purged);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_thumb_status ON flow_oss_object (thumb_status);

-- >>> flow_oss_upload_profile.sql
-- Table: flow_oss_upload_profile
-- OSS 上传场景（业务 Profile）
CREATE TABLE IF NOT EXISTS flow_oss_upload_profile (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  connection_code varchar(64) NOT NULL,
  visibility varchar(16) NOT NULL DEFAULT 'PRIVATE',
  bucket_override varchar(128),
  key_pattern varchar(512),
  allowed_content_types text,
  allowed_extensions varchar(512),
  max_size_bytes bigint,
  max_files_per_request integer DEFAULT 1,
  quota_max_bytes bigint,
  quota_max_files integer,
  thumbnail_enabled boolean NOT NULL DEFAULT false,
  thumbnail_max_edge integer,
  thumbnail_max_source_bytes bigint,
  thumbnail_jpeg_quality numeric(3,2),
  require_auth boolean NOT NULL DEFAULT true,
  presign_upload_enabled boolean NOT NULL DEFAULT false,
  biz_fields_schema text,
  upload_perm varchar(128),
  download_perm varchar(128),
  caller_policy text,
  enabled boolean NOT NULL DEFAULT true,
  remark varchar(512),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_upload_profile IS 'OSS 上传场景';
COMMENT ON COLUMN flow_oss_upload_profile.id IS '雪花ID';
COMMENT ON COLUMN flow_oss_upload_profile.code IS '场景编码（未删除记录内唯一）';
COMMENT ON COLUMN flow_oss_upload_profile.name IS '场景名称';
COMMENT ON COLUMN flow_oss_upload_profile.connection_code IS '绑定 OSS 连接编码';
COMMENT ON COLUMN flow_oss_upload_profile.visibility IS 'PUBLIC / PRIVATE';
COMMENT ON COLUMN flow_oss_upload_profile.bucket_override IS '桶覆盖（可空）';
COMMENT ON COLUMN flow_oss_upload_profile.key_pattern IS '对象键 pattern';
COMMENT ON COLUMN flow_oss_upload_profile.allowed_content_types IS 'Content-Type 白名单，逗号分隔';
COMMENT ON COLUMN flow_oss_upload_profile.allowed_extensions IS '扩展名白名单，逗号分隔';
COMMENT ON COLUMN flow_oss_upload_profile.max_size_bytes IS '单文件大小上限（字节）';
COMMENT ON COLUMN flow_oss_upload_profile.max_files_per_request IS '单次最多文件数';
COMMENT ON COLUMN flow_oss_upload_profile.quota_max_bytes IS '场景容量配额（字节），空=不限';
COMMENT ON COLUMN flow_oss_upload_profile.quota_max_files IS '场景文件数配额，空=不限';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_enabled IS '是否异步生成缩略图';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_max_edge IS '缩略图最长边像素，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_max_source_bytes IS '参与缩略图的源文件上限，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_jpeg_quality IS 'JPEG 质量 0~1，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.require_auth IS '上传是否必须登录';
COMMENT ON COLUMN flow_oss_upload_profile.presign_upload_enabled IS '是否开放预签名直传';
COMMENT ON COLUMN flow_oss_upload_profile.biz_fields_schema IS '业务字段 JSON Schema';
COMMENT ON COLUMN flow_oss_upload_profile.upload_perm IS '上传权限码；留空=仅 require_auth 控制';
COMMENT ON COLUMN flow_oss_upload_profile.download_perm IS '下载权限码；留空=仅 DataScope 控制';
COMMENT ON COLUMN flow_oss_upload_profile.caller_policy IS '访问规则 JSON：{"rules":[OssAccessRule]}';
COMMENT ON COLUMN flow_oss_upload_profile.enabled IS '启用状态';
COMMENT ON COLUMN flow_oss_upload_profile.remark IS '备注';
COMMENT ON COLUMN flow_oss_upload_profile.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_oss_upload_profile.create_time IS '创建时间';
COMMENT ON COLUMN flow_oss_upload_profile.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_code ON flow_oss_upload_profile (code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_connection_code ON flow_oss_upload_profile (connection_code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_enabled ON flow_oss_upload_profile (enabled);

-- >>> flow_page_directory.sql
-- Table: flow_page_directory
-- 页面目录表
CREATE TABLE IF NOT EXISTS flow_page_directory (
  id varchar(32) NOT NULL,
  parent_id varchar(32),
  name varchar(128) NOT NULL,
  sort integer DEFAULT 0,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_page_directory IS '页面目录表';
COMMENT ON COLUMN flow_page_directory.id IS '主键（雪花ID）';
COMMENT ON COLUMN flow_page_directory.parent_id IS '父节点ID，NULL 表示根节点';
COMMENT ON COLUMN flow_page_directory.name IS '目录名称';
COMMENT ON COLUMN flow_page_directory.sort IS '排序（升序）';
COMMENT ON COLUMN flow_page_directory.create_time IS '创建时间';
COMMENT ON COLUMN flow_page_directory.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_page_directory_parent_id ON flow_page_directory (parent_id);

-- >>> flow_page_info.sql
-- Table: flow_page_info
-- 页面信息表
CREATE TABLE IF NOT EXISTS flow_page_info (
  id varchar(32) NOT NULL,
  directory_id varchar(32),
  name varchar(256) NOT NULL,
  route_path varchar(512) NOT NULL,
  json text,
  status smallint DEFAULT 0,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_page_info_route_path UNIQUE (route_path)
);
COMMENT ON TABLE flow_page_info IS '页面信息表';
COMMENT ON COLUMN flow_page_info.id IS '主键（雪花ID）';
COMMENT ON COLUMN flow_page_info.directory_id IS '关联全局目录树';
COMMENT ON COLUMN flow_page_info.name IS '页面名称';
COMMENT ON COLUMN flow_page_info.route_path IS '访问路径（唯一）';
COMMENT ON COLUMN flow_page_info.json IS '页面配置 JSON Schema（Amis）';
COMMENT ON COLUMN flow_page_info.status IS '状态：0=草稿，1=已发布';
COMMENT ON COLUMN flow_page_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_page_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_page_info_directory_id ON flow_page_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_page_info_status ON flow_page_info (status);

-- >>> flow_regression_case.sql
-- Table: flow_regression_case
-- 回归用例
CREATE TABLE IF NOT EXISTS flow_regression_case (
  id varchar(64) NOT NULL,
  suite_id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  enabled smallint NOT NULL DEFAULT 1,
  headers_json text,
  query_json text,
  body text,
  expect_trace_status varchar(20),
  expect_json_path varchar(128),
  expect_value varchar(500),
  timeout_ms integer NOT NULL DEFAULT 10000,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_case IS '回归用例';
COMMENT ON COLUMN flow_regression_case.headers_json IS '请求头 JSON（禁止敏感头）';
COMMENT ON COLUMN flow_regression_case.query_json IS 'Query JSON';
COMMENT ON COLUMN flow_regression_case.body IS '请求体，≤32KB';
COMMENT ON COLUMN flow_regression_case.expect_trace_status IS 'success|error，空则不校验';
COMMENT ON COLUMN flow_regression_case.expect_json_path IS '简单 JSONPath';
COMMENT ON COLUMN flow_regression_case.expect_value IS '期望值（字符串比较）';
CREATE INDEX IF NOT EXISTS idx_flow_regression_case_suite_id ON flow_regression_case (suite_id);

-- >>> flow_regression_run_case.sql
-- Table: flow_regression_run_case
-- 回归用例运行明细
CREATE TABLE IF NOT EXISTS flow_regression_run_case (
  id varchar(64) NOT NULL,
  run_id varchar(64) NOT NULL,
  case_id varchar(64) NOT NULL,
  case_name varchar(100),
  status varchar(20) NOT NULL,
  duration_ms bigint,
  message varchar(500),
  detail_json varchar(2000),
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_run_case IS '回归用例运行明细';
COMMENT ON COLUMN flow_regression_run_case.status IS 'PASSED|FAILED|ERROR|SKIPPED';
COMMENT ON COLUMN flow_regression_run_case.detail_json IS '截断后的摘要，不含全量响应';
CREATE INDEX IF NOT EXISTS idx_flow_regression_run_case_run_id ON flow_regression_run_case (run_id);

-- >>> flow_regression_run.sql
-- Table: flow_regression_run
-- 回归运行记录
CREATE TABLE IF NOT EXISTS flow_regression_run (
  id varchar(64) NOT NULL,
  suite_id varchar(64) NOT NULL,
  asset_type varchar(32) NOT NULL,
  asset_id varchar(64) NOT NULL,
  env_code varchar(32) NOT NULL,
  status varchar(20) NOT NULL,
  total_cases integer NOT NULL DEFAULT 0,
  passed_cases integer NOT NULL DEFAULT 0,
  failed_cases integer NOT NULL DEFAULT 0,
  started_at timestamp NOT NULL,
  finished_at timestamp,
  triggered_by varchar(100),
  summary varchar(500),
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_run IS '回归运行记录';
COMMENT ON COLUMN flow_regression_run.status IS 'RUNNING|PASSED|FAILED|ERROR';
CREATE INDEX IF NOT EXISTS idx_flow_regression_run_asset_type_asset_id_env_code_finished_at ON flow_regression_run (asset_type, asset_id, env_code, finished_at);
CREATE INDEX IF NOT EXISTS idx_flow_regression_run_suite_id ON flow_regression_run (suite_id);

-- >>> flow_regression_suite.sql
-- Table: flow_regression_suite
-- 回归测试套件
CREATE TABLE IF NOT EXISTS flow_regression_suite (
  id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  asset_type varchar(32) NOT NULL,
  asset_id varchar(64) NOT NULL,
  enabled smallint NOT NULL DEFAULT 1,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_suite IS '回归测试套件';
COMMENT ON COLUMN flow_regression_suite.asset_type IS 'API|TASK|SERVICE';
CREATE INDEX IF NOT EXISTS idx_flow_regression_suite_asset_type_asset_id ON flow_regression_suite (asset_type, asset_id);

-- >>> flow_response_template.sql
-- Table: flow_response_template
-- API 响应模板表
CREATE TABLE IF NOT EXISTS flow_response_template (
  id varchar(32) NOT NULL,
  template_name varchar(100) NOT NULL,
  success_wrapper text,
  page_wrapper text,
  fail_wrapper text,
  is_default smallint NOT NULL DEFAULT 0,
  remark varchar(500),
  create_by varchar(64),
  create_time timestamp,
  update_by varchar(64),
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_response_template_template_name UNIQUE (template_name)
);
COMMENT ON TABLE flow_response_template IS 'API 响应模板表';
COMMENT ON COLUMN flow_response_template.id IS '主键 (雪花ID)';
COMMENT ON COLUMN flow_response_template.template_name IS '模板名称';
COMMENT ON COLUMN flow_response_template.success_wrapper IS '成功包装体 JSON 模板';
COMMENT ON COLUMN flow_response_template.page_wrapper IS '分页包装体 JSON 模板';
COMMENT ON COLUMN flow_response_template.fail_wrapper IS '失败包装体 JSON 模板';
COMMENT ON COLUMN flow_response_template.is_default IS '是否全局默认 (1:默认 0:非默认)';
COMMENT ON COLUMN flow_response_template.remark IS '备注说明';
COMMENT ON COLUMN flow_response_template.create_by IS '创建者';
COMMENT ON COLUMN flow_response_template.create_time IS '创建时间';
COMMENT ON COLUMN flow_response_template.update_by IS '更新者';
COMMENT ON COLUMN flow_response_template.update_time IS '更新时间';

-- >>> flow_service_info.sql
-- Table: flow_service_info
-- 内部服务编排定义
CREATE TABLE IF NOT EXISTS flow_service_info (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  directory_id varchar(32),
  enabled boolean NOT NULL DEFAULT true,
  log_enabled boolean NOT NULL DEFAULT true,
  log_mode varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
  dsl_content text,
  contract text,
  publish_status smallint NOT NULL DEFAULT 0,
  published_snapshot text,
  publish_time timestamp,
  info varchar(512),
  tags varchar(255),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_service_info IS '内部服务编排定义';
COMMENT ON COLUMN flow_service_info.id IS '雪花ID';
COMMENT ON COLUMN flow_service_info.name IS '服务名称';
COMMENT ON COLUMN flow_service_info.directory_id IS '关联目录ID（复用全局目录树）';
COMMENT ON COLUMN flow_service_info.enabled IS '启用状态：0=停用, 1=启用';
COMMENT ON COLUMN flow_service_info.log_enabled IS '是否记录执行日志';
COMMENT ON COLUMN flow_service_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';
COMMENT ON COLUMN flow_service_info.dsl_content IS '流程定义 DSL JSON（草稿）';
COMMENT ON COLUMN flow_service_info.contract IS '服务契约 JSON：inputs/outputs/outputDescription';
COMMENT ON COLUMN flow_service_info.publish_status IS '发布状态：0=未发布, 1=已发布';
COMMENT ON COLUMN flow_service_info.published_snapshot IS '发布快照 JSON：dslContent/contract';
COMMENT ON COLUMN flow_service_info.publish_time IS '最近发布时间';
COMMENT ON COLUMN flow_service_info.info IS '服务描述';
COMMENT ON COLUMN flow_service_info.tags IS '标签，英文逗号分隔';
COMMENT ON COLUMN flow_service_info.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_service_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_service_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_service_info_create_time ON flow_service_info (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_service_info_directory_id ON flow_service_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_service_info_enabled ON flow_service_info (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_service_info_publish_status ON flow_service_info (publish_status);

-- >>> flow_sys_config.sql
-- Table: flow_sys_config
-- 系统配置表 (System Configuration)
CREATE TABLE IF NOT EXISTS flow_sys_config (
  id varchar(32) NOT NULL,
  config_key varchar(100) NOT NULL,
  config_value text,
  value_type varchar(20) NOT NULL DEFAULT 'STRING',
  config_group varchar(50) NOT NULL DEFAULT 'GENERAL',
  remark varchar(500),
  is_builtin smallint NOT NULL DEFAULT 0,
  status smallint NOT NULL DEFAULT 1,
  sort_order integer NOT NULL DEFAULT 100,
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64),
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_config_config_key UNIQUE (config_key)
);
COMMENT ON TABLE flow_sys_config IS '系统配置表 (System Configuration)';
COMMENT ON COLUMN flow_sys_config.id IS '雪花ID';
COMMENT ON COLUMN flow_sys_config.config_key IS '配置键 (唯一标识，如: SYSTEM_PREFIX, TOKEN_EXPIRE)';
COMMENT ON COLUMN flow_sys_config.config_value IS '配置值 (支持字符串、数字、JSON 等格式)';
COMMENT ON COLUMN flow_sys_config.value_type IS '值类型 (STRING / NUMBER / BOOLEAN / JSON)';
COMMENT ON COLUMN flow_sys_config.config_group IS '配置分组 (GENERAL / SECURITY / OSS / GATEWAY 等)';
COMMENT ON COLUMN flow_sys_config.remark IS '配置说明';
COMMENT ON COLUMN flow_sys_config.is_builtin IS '是否内置 (1: 内置参数, 不允许删除; 0: 用户自定义)';
COMMENT ON COLUMN flow_sys_config.status IS '状态 (1: 启用, 0: 停用)';
COMMENT ON COLUMN flow_sys_config.sort_order IS '组内展示顺序，越小越靠前';
COMMENT ON COLUMN flow_sys_config.create_by IS '创建者';
COMMENT ON COLUMN flow_sys_config.create_time IS '创建时间';
COMMENT ON COLUMN flow_sys_config.update_by IS '更新者';
COMMENT ON COLUMN flow_sys_config.update_time IS '更新时间';

-- >>> flow_sys_macro.sql
-- Table: flow_sys_macro
-- 系统全局宏定义字典表 (Semantic Layer)
CREATE TABLE IF NOT EXISTS flow_sys_macro (
  id varchar(32) NOT NULL,
  macro_code varchar(100) NOT NULL,
  macro_name varchar(100) NOT NULL,
  macro_type varchar(50) NOT NULL,
  expression varchar(500) NOT NULL,
  scope varchar(50) NOT NULL DEFAULT 'ALL',
  return_type varchar(50),
  status smallint NOT NULL DEFAULT 1,
  remark varchar(500),
  macro_params varchar(255),
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64),
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_macro_macro_code UNIQUE (macro_code)
);
COMMENT ON TABLE flow_sys_macro IS '系统全局宏定义字典表 (Semantic Layer)';
COMMENT ON COLUMN flow_sys_macro.id IS '雪花ID';
COMMENT ON COLUMN flow_sys_macro.macro_code IS '宏编码 (前端调用的唯一凭证，如: sys_user_id)';
COMMENT ON COLUMN flow_sys_macro.macro_name IS '宏名称 (如: 当前登录用户 ID)';
COMMENT ON COLUMN flow_sys_macro.macro_type IS '宏类型 (枚举: VARIABLE 变量, FUNCTION 方法)';
COMMENT ON COLUMN flow_sys_macro.expression IS '真实的 SpEL 表达式 (如: @userContext.getUserId())';
COMMENT ON COLUMN flow_sys_macro.scope IS '作用域 (枚举: ALL 全局, SQL_ONLY 仅SQL, JS_ONLY 仅JS)';
COMMENT ON COLUMN flow_sys_macro.return_type IS '返回值类型 (用于前端 JS 类型推导提示，如 String, Number)';
COMMENT ON COLUMN flow_sys_macro.status IS '状态 (1: 启用, 0: 停用)';
COMMENT ON COLUMN flow_sys_macro.remark IS '备注说明';
COMMENT ON COLUMN flow_sys_macro.macro_params IS '入参列表 (仅 FUNCTION 类型有效，逗号分隔，如 date,format)';
COMMENT ON COLUMN flow_sys_macro.create_by IS '创建者';
COMMENT ON COLUMN flow_sys_macro.create_time IS '创建时间';
COMMENT ON COLUMN flow_sys_macro.update_by IS '更新者';
COMMENT ON COLUMN flow_sys_macro.update_time IS '更新时间';

-- >>> flow_sys_permission.sql
-- Table: flow_sys_permission
-- 权限点
CREATE TABLE IF NOT EXISTS flow_sys_permission (
  id varchar(32) NOT NULL,
  perm_code varchar(128) NOT NULL,
  perm_name varchar(64) NOT NULL,
  group_code varchar(64),
  remark varchar(255),
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_permission_perm_code UNIQUE (perm_code)
);
COMMENT ON TABLE flow_sys_permission IS '权限点';
COMMENT ON COLUMN flow_sys_permission.perm_code IS '如 flow:api:write';
COMMENT ON COLUMN flow_sys_permission.group_code IS '分组：flow/ops/infra/sys';

-- >>> flow_sys_role_permission.sql
-- Table: flow_sys_role_permission
-- 角色-权限
CREATE TABLE IF NOT EXISTS flow_sys_role_permission (
  role_id varchar(32) NOT NULL,
  perm_code varchar(128) NOT NULL,
  PRIMARY KEY (role_id, perm_code)
);
COMMENT ON TABLE flow_sys_role_permission IS '角色-权限';
CREATE INDEX IF NOT EXISTS idx_flow_sys_role_permission_perm_code ON flow_sys_role_permission (perm_code);

-- >>> flow_sys_role.sql
-- Table: flow_sys_role
-- 系统角色
CREATE TABLE IF NOT EXISTS flow_sys_role (
  id varchar(32) NOT NULL,
  role_code varchar(64) NOT NULL,
  role_name varchar(64) NOT NULL,
  status smallint NOT NULL DEFAULT 1,
  is_builtin smallint NOT NULL DEFAULT 0,
  remark varchar(255),
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_role_role_code UNIQUE (role_code)
);
COMMENT ON TABLE flow_sys_role IS '系统角色';
COMMENT ON COLUMN flow_sys_role.role_code IS 'ADMIN/OPERATOR/VIEWER';

-- >>> flow_sys_user_role.sql
-- Table: flow_sys_user_role
-- 用户-角色
CREATE TABLE IF NOT EXISTS flow_sys_user_role (
  user_id varchar(32) NOT NULL,
  role_id varchar(32) NOT NULL,
  PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE flow_sys_user_role IS '用户-角色';
CREATE INDEX IF NOT EXISTS idx_flow_sys_user_role_role_id ON flow_sys_user_role (role_id);

-- >>> flow_sys_user.sql
-- Table: flow_sys_user
-- 系统用户
CREATE TABLE IF NOT EXISTS flow_sys_user (
  id varchar(32) NOT NULL,
  username varchar(64) NOT NULL,
  password_hash varchar(128) NOT NULL,
  display_name varchar(64),
  status smallint NOT NULL DEFAULT 1,
  is_builtin smallint NOT NULL DEFAULT 0,
  remark varchar(255),
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_user_username UNIQUE (username)
);
COMMENT ON TABLE flow_sys_user IS '系统用户';
COMMENT ON COLUMN flow_sys_user.username IS '登录名';
COMMENT ON COLUMN flow_sys_user.password_hash IS 'BCrypt 密码';
COMMENT ON COLUMN flow_sys_user.display_name IS '显示名';
COMMENT ON COLUMN flow_sys_user.status IS '1启用 0停用';
COMMENT ON COLUMN flow_sys_user.is_builtin IS '1内置不可删';

-- >>> flow_task_info.sql
-- Table: flow_task_info
-- 定时任务定义
CREATE TABLE IF NOT EXISTS flow_task_info (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  directory_id varchar(32),
  cron varchar(64) NOT NULL,
  -- 与现网瀚高一致：boolean（Hibernate Boolean / findByEnabled(true)）
  enabled boolean NOT NULL DEFAULT true,
  log_enabled boolean NOT NULL DEFAULT true,
  log_mode varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
  log_retention_days integer,
  dsl_content text,
  publish_status smallint NOT NULL DEFAULT 0,
  published_snapshot text,
  publish_time timestamp,
  info varchar(512),
  tags varchar(255),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_task_info IS '定时任务定义';
COMMENT ON COLUMN flow_task_info.id IS '雪花ID';
COMMENT ON COLUMN flow_task_info.name IS '任务名称';
COMMENT ON COLUMN flow_task_info.directory_id IS '关联目录ID（复用全局目录树）';
COMMENT ON COLUMN flow_task_info.cron IS 'Cron 表达式，如 0/5 * * * * ?';
COMMENT ON COLUMN flow_task_info.log_enabled IS '是否记录执行日志';
COMMENT ON COLUMN flow_task_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';
COMMENT ON COLUMN flow_task_info.log_retention_days IS '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数';
COMMENT ON COLUMN flow_task_info.dsl_content IS '流程定义 DSL JSON（草稿）';
COMMENT ON COLUMN flow_task_info.publish_status IS '发布状态：0=未发布，1=已发布';
COMMENT ON COLUMN flow_task_info.published_snapshot IS '发布快照 JSON：dslContent';
COMMENT ON COLUMN flow_task_info.publish_time IS '最近发布时间';
COMMENT ON COLUMN flow_task_info.info IS '任务描述';
COMMENT ON COLUMN flow_task_info.tags IS '标签，英文逗号分隔';
COMMENT ON COLUMN flow_task_info.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_task_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_task_info.update_time IS '更新时间';
COMMENT ON COLUMN flow_task_info.enabled IS '启用状态：0=停用, 1=启用';
CREATE INDEX IF NOT EXISTS idx_flow_task_info_create_time ON flow_task_info (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_task_info_directory_id ON flow_task_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_task_info_enabled ON flow_task_info (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_task_info_publish_status ON flow_task_info (publish_status);

