-- Yu Flow MySQL schema (generated from live DB)
-- database: flow
-- index naming: uk_{table}_{cols} / idx_{table}_{cols} (schema-unique, PG-compatible)

SET NAMES utf8mb4;

-- Table: flow_alert_channel
-- 告警通道
CREATE TABLE IF NOT EXISTS `flow_alert_channel` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '通道名称',
  `type` varchar(20) NOT NULL COMMENT 'WEBHOOK | EMAIL',
  `config_json` text COMMENT '通道配置 JSON',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_alert_channel_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警通道';

-- Table: flow_alert_event
-- 告警事件历史
CREATE TABLE IF NOT EXISTS `flow_alert_event` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `rule_id` varchar(64) COMMENT '规则 ID，SysConfig 兜底为空',
  `rule_name` varchar(100),
  `fingerprint` varchar(200) COMMENT '去重指纹',
  `asset_type` varchar(32),
  `asset_id` varchar(64),
  `asset_name` varchar(200),
  `health` varchar(20),
  `error_rate` double,
  `fail_count` bigint,
  `window` varchar(20),
  `channel_type` varchar(20),
  `channel_id` varchar(64),
  `status` varchar(20) NOT NULL COMMENT 'SUCCESS|FAIL|SUPPRESSED',
  `payload_json` text,
  `error_msg` varchar(500),
  `fired_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_flow_alert_event_fired_at` (`fired_at`),
  KEY `idx_flow_alert_event_rule_id` (`rule_id`),
  KEY `idx_flow_alert_event_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件历史';

-- Table: flow_alert_rule
-- 告警规则
CREATE TABLE IF NOT EXISTS `flow_alert_rule` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '规则名称',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `scope_asset_types` varchar(200) COMMENT '资产类型 CSV：API,TASK,SERVICE,PLATFORM；空=全部',
  `window` varchar(20) NOT NULL DEFAULT '24h' COMMENT '1h/24h/7d',
  `min_health` varchar(20) NOT NULL DEFAULT 'error' COMMENT 'error|warn',
  `top_n` int NOT NULL DEFAULT 10,
  `channel_ids` varchar(500) COMMENT '通道 ID JSON 数组',
  `interval_minutes` int NOT NULL DEFAULT 15,
  `dedup_minutes` int NOT NULL DEFAULT 60,
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_alert_rule_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则';

-- Table: flow_api_excel_template
-- API Excel 导出模板
CREATE TABLE IF NOT EXISTS `flow_api_excel_template` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `api_id` varchar(64) NOT NULL COMMENT '接口 ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `content_type` varchar(120) COMMENT 'MIME',
  `content` mediumblob NOT NULL COMMENT 'xlsx 二进制',
  `file_size` int NOT NULL DEFAULT 0,
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_api_excel_template_api_id` (`api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Excel 导出模板';

-- Table: flow_api_info
-- 接口配置类
CREATE TABLE IF NOT EXISTS `flow_api_info` (
  `id` bigint NOT NULL COMMENT '主键ID，通过Snowflake算法生成',
  `name` varchar(20) COMMENT 'API配置的名称',
  `directory_id` varchar(64) COMMENT '关联全局目录树',
  `url` varchar(100) COMMENT 'API的URL路径',
  `datasource` varchar(20) COMMENT '数据源名称',
  `module` varchar(20) COMMENT '所属模块名称',
  `method` varchar(10) COMMENT '请求方式：POST、PUT、GET、DELETE',
  `service_type` varchar(20) COMMENT '服务驱动类型：DB、FLOW、JSON、STRING',
  `response_type` varchar(10) COMMENT '响应数据类型：PAGE(分页)、LIST(列表)、OBJECT(对象)',
  `version` varchar(20) COMMENT 'API版本号',
  `config` longtext COMMENT '核心逻辑配置，存储SQL、流编排JSON或静态数据',
  `publish_status` smallint COMMENT '发布状态：0：未发布；1：已发布',
  `contract` mediumtext,
  `tags` varchar(500) COMMENT '标签，英文都好分隔',
  `level` int COMMENT '优先级，与请求的ss-level比较，大的优先',
  `template_id` varchar(32) COMMENT '基座模板ID',
  `custom_success_wrapper` text COMMENT '自定义成功返回包装',
  `custom_page_wrapper` text COMMENT '自定义分页返回包装',
  `custom_fail_wrapper` text COMMENT '自定义失败返回包装',
  `info` text COMMENT 'API配置的详细描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，自动记录为当前时间',
  `update_time` datetime COMMENT '更新时间',
  `deleted` int DEFAULT 0,
  `dsl_content` mediumtext COMMENT '逻辑编排 (FLOW) — Flow DSL JSON',
  `sql_content` text COMMENT '数据库 (DB) — SQL 脚本',
  `json_content` mediumtext COMMENT '静态 JSON (JSON) — JSON 内容',
  `text_content` text COMMENT '静态文本 (STRING) — 纯文本内容',
  `published_snapshot` mediumtext COMMENT '发布时的完整内容快照 (JSON)，运行时引擎从此字段读取',
  `publish_time` datetime COMMENT '最近一次发布时间',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志：1-开启，0-关闭',
  `cache_config` text COMMENT '响应缓存配置 JSON：enabled/ttlSeconds/keyParams/includePageable',
  `security_config` text COMMENT '入站防护 JSON：authMode/antiReplay/rateLimit/ipAllowlist',
  `view_export_config` text COMMENT '数据查看与导出 JSON：columns/sheetName/maxExportRows',
  PRIMARY KEY (`id`),
  KEY `idx_flow_api_info_directory_id` (`directory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口配置类';

-- Table: flow_asset_version
-- 资产发布历史版本
CREATE TABLE IF NOT EXISTS `flow_asset_version` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `biz_type` varchar(16) NOT NULL COMMENT '资产类型：api / task / service',
  `asset_id` varchar(32) NOT NULL COMMENT '资产ID',
  `version_no` int NOT NULL COMMENT '同资产内递增版本号',
  `snapshot` mediumtext NOT NULL COMMENT '发布快照 JSON（与各模块 published_snapshot 同构）',
  `source` varchar(16) NOT NULL DEFAULT 'publish' COMMENT '来源：publish / rollback',
  `remark` varchar(255) COMMENT '备注',
  `publisher` varchar(64) COMMENT '发布人',
  `publish_time` datetime NOT NULL COMMENT '发布时间',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_asset_version_biz_type_asset_id_publish_time` (`biz_type`, `asset_id`, `publish_time`),
  UNIQUE KEY `uk_flow_asset_version_biz_type_asset_id_version_no` (`biz_type`, `asset_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产发布历史版本';

-- Table: flow_datasource
-- 动态数据源配置表
CREATE TABLE IF NOT EXISTS `flow_datasource` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '数据源名称',
  `db_type` varchar(20) NOT NULL COMMENT '数据库类型(mysql/postgresql/highgo)',
  `driver_class_name` varchar(200) NOT NULL COMMENT '驱动类名',
  `url` varchar(500) NOT NULL COMMENT 'JDBC URL',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `initial_size` int DEFAULT 5 COMMENT '初始连接数',
  `min_idle` int DEFAULT 5 COMMENT '最小空闲连接',
  `max_active` int DEFAULT 20 COMMENT '最大活动连接',
  `status` tinyint DEFAULT 1 COMMENT '状态(0-停用,1-启用)',
  `wall_config` text COMMENT 'SQL安全墙JSON(DataSourceWallConfig)',
  `is_system` tinyint NOT NULL DEFAULT 0 COMMENT '系统数据源(1=不可删改连接，如[DEFAULT])',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `code` varchar(50) COMMENT '数据源全局唯一编码，用于跨环境关联',
  `health_status` varchar(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '连接健康度：HEALTHY-健康, UNHEALTHY-异常, UNKNOWN-未知',
  `error_count` int NOT NULL DEFAULT 0 COMMENT '连续连接失败次数',
  `last_error_msg` text COMMENT '最后一次连接失败的异常堆栈/简述',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_datasource_name` (`name`),
  UNIQUE KEY `uk_flow_datasource_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态数据源配置表';

-- Table: flow_directory
-- 全局目录表
CREATE TABLE IF NOT EXISTS `flow_directory` (
  `id` varchar(64) NOT NULL COMMENT '主键（雪花ID）',
  `parent_id` varchar(64) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `biz_type` varchar(32) COMMENT '业务域：api/task/service/model/page，空=共用',
  `sort` int DEFAULT 0 COMMENT '排序（升序）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_directory_biz_type` (`biz_type`),
  KEY `idx_flow_directory_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局目录表';

-- Table: flow_env
-- 发布逻辑环境
CREATE TABLE IF NOT EXISTS `flow_env` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `code` varchar(32) NOT NULL COMMENT 'DEV|STAGING|PROD',
  `name` varchar(100) NOT NULL COMMENT '显示名',
  `require_suite_pass` tinyint NOT NULL DEFAULT 0 COMMENT '1=发布前需回归通过',
  `pass_ttl_hours` int NOT NULL DEFAULT 24 COMMENT '通过结果有效小时数',
  `enabled` tinyint NOT NULL DEFAULT 1,
  `sort_order` int NOT NULL DEFAULT 0,
  `remark` varchar(500),
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_env_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布逻辑环境';

-- Table: flow_log_audit
-- 配置变更审计
CREATE TABLE IF NOT EXISTS `flow_log_audit` (
  `id` varchar(64) NOT NULL,
  `action` varchar(64) NOT NULL COMMENT 'API_PUBLISH|SYS_CONFIG_UPDATE|OPEN_SECRET_ROTATE',
  `operator` varchar(100) COMMENT '操作人',
  `target_type` varchar(64) COMMENT 'API|SYS_CONFIG|OPEN_CREDENTIAL',
  `target_id` varchar(64),
  `detail` varchar(1024) COMMENT 'JSON 摘要，不含密钥',
  `create_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_audit_action_create_time` (`action`, `create_time`),
  KEY `idx_flow_log_audit_operator` (`operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置变更审计';

-- Table: flow_log_execution
-- API执行日志表
CREATE TABLE IF NOT EXISTS `flow_log_execution` (
  `id` varchar(32) NOT NULL COMMENT '主键ID',
  `api_id` varchar(32) COMMENT 'API ID',
  `api_name` varchar(128) COMMENT 'API名称',
  `url` varchar(255) COMMENT '请求URL',
  `method` varchar(16) COMMENT '请求方法',
  `request_params` longtext COMMENT '请求参数快照(JSON)',
  `response_body` longtext COMMENT '返回结果快照(JSON)',
  `status` varchar(16) COMMENT '执行状态: SUCCESS/ERROR',
  `error_msg` text,
  `cost_time_ms` bigint COMMENT '耗时(毫秒)',
  `trace_data` longtext COMMENT '完整追踪快照(如有)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `service_type` varchar(32) COMMENT '接口类型: FLOW/DB/JSON/STRING',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_execution_api_id` (`api_id`),
  KEY `idx_flow_log_execution_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API执行日志表';

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

-- Table: flow_log_open_call
-- 开放平台入站调用摘要
CREATE TABLE IF NOT EXISTS `flow_log_open_call` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `platform_id` varchar(32) COMMENT '开放平台ID',
  `app_key` varchar(64) COMMENT '调用方 AppKey',
  `api_id` varchar(32) COMMENT '命中的 API ID',
  `method` varchar(16) COMMENT 'HTTP 方法',
  `path` varchar(512) COMMENT '真实发布路径',
  `status` int COMMENT 'HTTP 状态',
  `cost_ms` bigint COMMENT '耗时毫秒',
  `error_code` varchar(64) COMMENT '业务/鉴权错误码',
  `request_id` varchar(64) COMMENT '请求追踪ID',
  `create_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_open_call_app_key` (`app_key`),
  KEY `idx_flow_log_open_call_create_time` (`create_time`),
  KEY `idx_flow_log_open_call_platform_id_create_time` (`platform_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台入站调用摘要';

-- Table: flow_log_service
-- 内部服务编排执行日志
CREATE TABLE IF NOT EXISTS `flow_log_service` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `service_id` varchar(32) NOT NULL COMMENT '关联服务ID',
  `service_name` varchar(128) COMMENT '服务名称（冗余）',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '触发类型：MANUAL=手动, CALL=流程内调用, DEBUG=调试',
  `status` varchar(16) NOT NULL COMMENT '执行状态：SUCCESS / FAILED / RUNNING',
  `cost_time_ms` bigint COMMENT '耗时（毫秒）',
  `error_msg` text COMMENT '失败信息',
  `trace_data` longtext COMMENT 'FlowTrace JSON 快照（logEnabled=true 时记录）',
  `create_time` datetime COMMENT '执行开始时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_service_create_time` (`create_time`),
  KEY `idx_flow_log_service_service_id` (`service_id`),
  KEY `idx_flow_log_service_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部服务编排执行日志';

-- Table: flow_log_task
-- 定时任务执行日志
CREATE TABLE IF NOT EXISTS `flow_log_task` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `task_id` varchar(32) NOT NULL COMMENT '关联任务ID',
  `task_name` varchar(128) COMMENT '任务名称（冗余）',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'CRON' COMMENT '触发类型：CRON=定时, MANUAL=手动',
  `status` varchar(16) NOT NULL COMMENT '执行状态：SUCCESS / FAILED / RUNNING',
  `cost_time_ms` bigint COMMENT '耗时（毫秒）',
  `error_msg` text COMMENT '失败信息',
  `trace_data` longtext COMMENT 'FlowTrace JSON 快照（logEnabled=true 时记录）',
  `create_time` datetime COMMENT '执行开始时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_task_create_time` (`create_time`),
  KEY `idx_flow_log_task_status` (`status`),
  KEY `idx_flow_log_task_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行日志';

-- Table: flow_log_third
-- 第三方接口调用日志
CREATE TABLE IF NOT EXISTS `flow_log_third` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `api_type` varchar(50) COMMENT '接口标识',
  `source` varchar(16) COMMENT '调用来源：API/TASK/DEBUG/OTHER',
  `source_ref` varchar(64) COMMENT '来源关联ID（apiId/taskId）',
  `source_name` varchar(128) COMMENT '来源名称（接口名/任务名）',
  `request_url` varchar(512) COMMENT '请求URL',
  `request_method` varchar(10) COMMENT '请求方法',
  `request_params` text COMMENT '请求参数',
  `request_headers` text COMMENT '请求头',
  `response_status` int COMMENT '响应状态码',
  `response_body` text COMMENT '响应内容',
  `elapsed_time` bigint COMMENT '请求耗时(ms)',
  `is_success` tinyint COMMENT '是否成功（0失败/1成功）',
  `error_message` varchar(1000) COMMENT '错误信息',
  `curl` text COMMENT 'Curl命令',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_third_api_type` (`api_type`),
  KEY `idx_flow_log_third_create_time` (`create_time`),
  KEY `idx_flow_log_third_source` (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方接口调用日志';

-- Table: flow_metrics_meta
-- 资产运行计量元数据
CREATE TABLE IF NOT EXISTS `flow_metrics_meta` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `asset_type` varchar(16) NOT NULL COMMENT 'API / TASK / SERVICE / PLATFORM',
  `asset_id` varchar(32) NOT NULL COMMENT '资产ID',
  `last_success_at` bigint COMMENT '最近成功 epoch ms',
  `last_fail_at` bigint COMMENT '最近业务失败 epoch ms',
  `consec_fail` bigint NOT NULL DEFAULT 0 COMMENT '连续业务失败次数',
  `update_time` datetime COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_metrics_meta_asset_type` (`asset_type`),
  UNIQUE KEY `uk_flow_metrics_meta_asset_type_asset_id` (`asset_type`, `asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量元数据';

-- Table: flow_metrics_minute
-- 资产运行计量分钟汇总
CREATE TABLE IF NOT EXISTS `flow_metrics_minute` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `asset_type` varchar(16) NOT NULL COMMENT 'API / TASK / SERVICE',
  `asset_id` varchar(32) NOT NULL COMMENT '资产ID',
  `trigger_type` varchar(16) NOT NULL DEFAULT '_' COMMENT '触发类型；API 固定为 _',
  `bucket_start` datetime NOT NULL COMMENT '分钟桶起点（整分）',
  `success_cnt` bigint NOT NULL DEFAULT 0,
  `fail_cnt` bigint NOT NULL DEFAULT 0,
  `auth_fail_cnt` bigint NOT NULL DEFAULT 0 COMMENT '鉴权失败次数（如开放平台 401/403）',
  `skipped_cnt` bigint NOT NULL DEFAULT 0,
  `sum_cost_ms` bigint NOT NULL DEFAULT 0,
  `latency_count` bigint NOT NULL DEFAULT 0,
  `hist_json` varchar(1024) COMMENT '直方图桶计数 JSON 数组',
  `update_time` datetime COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_metrics_minute_bucket_start` (`bucket_start`),
  KEY `idx_flow_metrics_minute_asset_type_bucket_start` (`asset_type`, `bucket_start`),
  UNIQUE KEY `uk_flow_metrics_minute_asset_type_asset_id_trigger_type_buck_967` (`asset_type`, `asset_id`, `trigger_type`, `bucket_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量分钟汇总';

-- Table: flow_model_directory
-- 数据模型目录表
CREATE TABLE IF NOT EXISTS `flow_model_directory` (
  `id` varchar(64) NOT NULL COMMENT '主键（雪花ID）',
  `parent_id` varchar(64) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `sort` int DEFAULT 0 COMMENT '排序（升序）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_model_directory_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型目录表';

-- Table: flow_model_info
-- 数据模型信息表
CREATE TABLE IF NOT EXISTS `flow_model_info` (
  `id` varchar(64) NOT NULL COMMENT '主键（雪花ID）',
  `directory_id` varchar(64) COMMENT '关联全局目录树',
  `name` varchar(256) NOT NULL COMMENT '模型中文名，如：用户信息',
  `table_name` varchar(256) NOT NULL COMMENT '底层物理表名，如：t_user',
  `fields_schema` longtext COMMENT '核心元数据 JSON 数组（包含字段名、类型、UI配置等）',
  `status` tinyint DEFAULT 0 COMMENT '状态：0=停用，1=启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `datasource` varchar(50) COMMENT '关联的动态数据源 code',
  PRIMARY KEY (`id`),
  KEY `idx_flow_model_info_directory_id` (`directory_id`),
  KEY `idx_flow_model_info_status` (`status`),
  UNIQUE KEY `uk_flow_model_info_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型信息表';

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

-- Table: flow_open_platform
-- 第三方开放平台
CREATE TABLE IF NOT EXISTS `flow_open_platform` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '平台名称',
  `code` varchar(64) NOT NULL COMMENT '唯一编码',
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

-- Table: flow_page_directory
-- 页面目录表
CREATE TABLE IF NOT EXISTS `flow_page_directory` (
  `id` varchar(64) NOT NULL COMMENT '主键（雪花ID）',
  `parent_id` varchar(64) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `sort` int DEFAULT 0 COMMENT '排序（升序）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_page_directory_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面目录表';

-- Table: flow_page_info
-- 页面信息表
CREATE TABLE IF NOT EXISTS `flow_page_info` (
  `id` varchar(64) NOT NULL COMMENT '主键（雪花ID）',
  `directory_id` varchar(64) COMMENT '关联全局目录树',
  `name` varchar(256) NOT NULL COMMENT '页面名称',
  `route_path` varchar(512) NOT NULL COMMENT '访问路径（唯一）',
  `json` longtext COMMENT '页面配置 JSON Schema（Amis）',
  `status` tinyint DEFAULT 0 COMMENT '状态：0=草稿，1=已发布',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_page_info_directory_id` (`directory_id`),
  KEY `idx_flow_page_info_status` (`status`),
  UNIQUE KEY `uk_flow_page_info_route_path` (`route_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面信息表';

-- Table: flow_regression_case
-- 回归用例
CREATE TABLE IF NOT EXISTS `flow_regression_case` (
  `id` varchar(64) NOT NULL,
  `suite_id` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `headers_json` text COMMENT '请求头 JSON（禁止敏感头）',
  `query_json` text COMMENT 'Query JSON',
  `body` mediumtext COMMENT '请求体，≤32KB',
  `expect_trace_status` varchar(20) COMMENT 'success|error，空则不校验',
  `expect_json_path` varchar(128) COMMENT '简单 JSONPath',
  `expect_value` varchar(500) COMMENT '期望值（字符串比较）',
  `timeout_ms` int NOT NULL DEFAULT 10000,
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_regression_case_suite_id` (`suite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归用例';

-- Table: flow_regression_run
-- 回归运行记录
CREATE TABLE IF NOT EXISTS `flow_regression_run` (
  `id` varchar(64) NOT NULL,
  `suite_id` varchar(64) NOT NULL,
  `asset_type` varchar(32) NOT NULL,
  `asset_id` varchar(64) NOT NULL,
  `env_code` varchar(32) NOT NULL,
  `status` varchar(20) NOT NULL COMMENT 'RUNNING|PASSED|FAILED|ERROR',
  `total_cases` int NOT NULL DEFAULT 0,
  `passed_cases` int NOT NULL DEFAULT 0,
  `failed_cases` int NOT NULL DEFAULT 0,
  `started_at` datetime NOT NULL,
  `finished_at` datetime,
  `triggered_by` varchar(100),
  `summary` varchar(500),
  PRIMARY KEY (`id`),
  KEY `idx_flow_regression_run_asset_type_asset_id_env_code_finished_at` (`asset_type`, `asset_id`, `env_code`, `finished_at`),
  KEY `idx_flow_regression_run_suite_id` (`suite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归运行记录';

-- Table: flow_regression_run_case
-- 回归用例运行明细
CREATE TABLE IF NOT EXISTS `flow_regression_run_case` (
  `id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `case_id` varchar(64) NOT NULL,
  `case_name` varchar(100),
  `status` varchar(20) NOT NULL COMMENT 'PASSED|FAILED|ERROR|SKIPPED',
  `duration_ms` bigint,
  `message` varchar(500),
  `detail_json` varchar(2000) COMMENT '截断后的摘要，不含全量响应',
  PRIMARY KEY (`id`),
  KEY `idx_flow_regression_run_case_run_id` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归用例运行明细';

-- Table: flow_regression_suite
-- 回归测试套件
CREATE TABLE IF NOT EXISTS `flow_regression_suite` (
  `id` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `asset_type` varchar(32) NOT NULL COMMENT 'API|TASK|SERVICE',
  `asset_id` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  KEY `idx_flow_regression_suite_asset_type_asset_id` (`asset_type`, `asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归测试套件';

-- Table: flow_response_template
-- API 响应模板表
CREATE TABLE IF NOT EXISTS `flow_response_template` (
  `id` varchar(32) NOT NULL COMMENT '主键 (雪花ID)',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `success_wrapper` text COMMENT '成功包装体 JSON 模板',
  `page_wrapper` text COMMENT '分页包装体 JSON 模板',
  `fail_wrapper` text COMMENT '失败包装体 JSON 模板',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否全局默认 (1:默认 0:非默认)',
  `remark` varchar(500) COMMENT '备注说明',
  `create_by` varchar(64) COMMENT '创建者',
  `create_time` datetime COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新者',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_response_template_template_name` (`template_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 响应模板表';


-- Table: flow_service_info
-- 内部服务编排定义
CREATE TABLE IF NOT EXISTS `flow_service_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '服务名称',
  `directory_id` varchar(32) COMMENT '关联目录ID（复用全局目录树）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
  `dsl_content` mediumtext COMMENT '流程定义 DSL JSON（草稿）',
  `contract` mediumtext COMMENT '服务契约 JSON：inputs/outputs/outputDescription',
  `publish_status` tinyint NOT NULL DEFAULT 0 COMMENT '发布状态：0=未发布, 1=已发布',
  `published_snapshot` mediumtext COMMENT '发布快照 JSON：dslContent/contract',
  `publish_time` datetime COMMENT '最近发布时间',
  `info` varchar(512) COMMENT '服务描述',
  `tags` varchar(255) COMMENT '标签，英文逗号分隔',
  `deleted` int NOT NULL DEFAULT 0 COMMENT '软删除：0=正常, 1=已删除',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_service_info_create_time` (`create_time`),
  KEY `idx_flow_service_info_directory_id` (`directory_id`),
  KEY `idx_flow_service_info_enabled` (`enabled`),
  KEY `idx_flow_service_info_publish_status` (`publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部服务编排定义';

-- Table: flow_sys_config
-- 系统配置表 (System Configuration)
CREATE TABLE IF NOT EXISTS `flow_sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键 (唯一标识，如: SYSTEM_PREFIX, TOKEN_EXPIRE)',
  `config_value` text COMMENT '配置值 (支持字符串、数字、JSON 等格式)',
  `value_type` varchar(20) NOT NULL DEFAULT 'STRING' COMMENT '值类型 (STRING / NUMBER / BOOLEAN / JSON)',
  `config_group` varchar(50) NOT NULL DEFAULT 'GENERAL' COMMENT '配置分组 (GENERAL / SECURITY / OSS / GATEWAY 等)',
  `remark` varchar(500) COMMENT '配置说明',
  `is_builtin` tinyint NOT NULL DEFAULT 0 COMMENT '是否内置 (1: 内置参数, 不允许删除; 0: 用户自定义)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态 (1: 启用, 0: 停用)',
  `sort_order` int NOT NULL DEFAULT 100 COMMENT '组内展示顺序，越小越靠前',
  `create_by` varchar(64) COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_config_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表 (System Configuration)';

-- Table: flow_sys_macro
-- 系统全局宏定义字典表 (Semantic Layer)
CREATE TABLE IF NOT EXISTS `flow_sys_macro` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `macro_code` varchar(100) NOT NULL COMMENT '宏编码 (前端调用的唯一凭证，如: sys_user_id)',
  `macro_name` varchar(100) NOT NULL COMMENT '宏名称 (如: 当前登录用户 ID)',
  `macro_type` varchar(50) NOT NULL COMMENT '宏类型 (枚举: VARIABLE 变量, FUNCTION 方法)',
  `expression` varchar(500) NOT NULL COMMENT '真实的 SpEL 表达式 (如: @userContext.getUserId())',
  `scope` varchar(50) NOT NULL DEFAULT 'ALL' COMMENT '作用域 (枚举: ALL 全局, SQL_ONLY 仅SQL, JS_ONLY 仅JS)',
  `return_type` varchar(50) COMMENT '返回值类型 (用于前端 JS 类型推导提示，如 String, Number)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态 (1: 启用, 0: 停用)',
  `remark` varchar(500) COMMENT '备注说明',
  `create_by` varchar(64) COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `macro_params` varchar(255) COMMENT '入参列表 (仅 FUNCTION 类型有效，逗号分隔，如 date,format)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_macro_macro_code` (`macro_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统全局宏定义字典表 (Semantic Layer)';

-- Table: flow_sys_permission
-- 权限点
CREATE TABLE IF NOT EXISTS `flow_sys_permission` (
  `id` varchar(32) NOT NULL,
  `perm_code` varchar(128) NOT NULL COMMENT '如 flow:api:write',
  `perm_name` varchar(64) NOT NULL,
  `group_code` varchar(64) COMMENT '分组：flow/ops/infra/sys',
  `remark` varchar(255),
  `create_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_permission_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';

-- Table: flow_sys_role
-- 系统角色
CREATE TABLE IF NOT EXISTS `flow_sys_role` (
  `id` varchar(32) NOT NULL,
  `role_code` varchar(64) NOT NULL COMMENT 'ADMIN/OPERATOR/VIEWER',
  `role_name` varchar(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `is_builtin` tinyint NOT NULL DEFAULT 0,
  `remark` varchar(255),
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_role_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';

-- Table: flow_sys_role_permission
-- 角色-权限
CREATE TABLE IF NOT EXISTS `flow_sys_role_permission` (
  `role_id` varchar(32) NOT NULL,
  `perm_code` varchar(128) NOT NULL,
  PRIMARY KEY (`role_id`, `perm_code`),
  KEY `idx_flow_sys_role_permission_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';

-- Table: flow_sys_user
-- 系统用户
CREATE TABLE IF NOT EXISTS `flow_sys_user` (
  `id` varchar(32) NOT NULL,
  `username` varchar(64) NOT NULL COMMENT '登录名',
  `password_hash` varchar(128) NOT NULL COMMENT 'BCrypt 密码',
  `display_name` varchar(64) COMMENT '显示名',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `is_builtin` tinyint NOT NULL DEFAULT 0 COMMENT '1内置不可删',
  `remark` varchar(255),
  `create_time` datetime,
  `update_time` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- Table: flow_sys_user_role
-- 用户-角色
CREATE TABLE IF NOT EXISTS `flow_sys_user_role` (
  `user_id` varchar(32) NOT NULL,
  `role_id` varchar(32) NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_flow_sys_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

-- Table: flow_task_info
-- 定时任务定义
CREATE TABLE IF NOT EXISTS `flow_task_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '任务名称',
  `directory_id` varchar(32) COMMENT '关联目录ID（复用全局目录树）',
  `cron` varchar(64) NOT NULL COMMENT 'Cron 表达式，如 0/5 * * * * ?',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
  `dsl_content` mediumtext COMMENT '流程定义 DSL JSON（草稿）',
  `publish_status` tinyint NOT NULL DEFAULT 0 COMMENT '发布状态：0=未发布，1=已发布',
  `published_snapshot` mediumtext COMMENT '发布快照 JSON：dslContent',
  `publish_time` datetime COMMENT '最近发布时间',
  `info` varchar(512) COMMENT '任务描述',
  `tags` varchar(255) COMMENT '标签，英文逗号分隔',
  `deleted` int NOT NULL DEFAULT 0 COMMENT '软删除：0=正常, 1=已删除',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_task_info_create_time` (`create_time`),
  KEY `idx_flow_task_info_directory_id` (`directory_id`),
  KEY `idx_flow_task_info_enabled` (`enabled`),
  KEY `idx_flow_task_info_publish_status` (`publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务定义';
