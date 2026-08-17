-- Yu Flow MySQL 全量建表（由 sql-mysql/flow_*.sql 汇总生成，勿手工穿插重复表）
-- 生成时间: 2026-08-17T10:24:37.964Z
-- 用法: 先执行本文件，再执行 00_system_init.sql

-- >>> flow_alert_channel.sql
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

-- >>> flow_alert_event.sql
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

-- >>> flow_alert_rule.sql
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

-- >>> flow_api_excel_template.sql
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

-- >>> flow_api_info.sql
-- Table: flow_api_info
-- 接口配置类
CREATE TABLE IF NOT EXISTS `flow_api_info` (
  `id` bigint NOT NULL COMMENT '主键ID，通过Snowflake算法生成',
  `name` varchar(20) COMMENT 'API配置的名称',
  `directory_id` varchar(32) COMMENT '关联全局目录树',
  `url` varchar(100) COMMENT 'API的URL路径',
  `datasource` varchar(20) COMMENT '数据源名称',
  `module` varchar(20) COMMENT '所属模块名称',
  `method` varchar(10) COMMENT '请求方式：POST、PUT、GET、DELETE',
  `service_type` varchar(20) COMMENT '服务驱动类型：DB、FLOW、JSON、STRING、HOST',
  `intercept_mode` varchar(16) NOT NULL DEFAULT 'REPLACE' COMMENT '同名拦截：REPLACE-替换执行引擎；WRAP-包裹转发宿主',
  `host_binding` text COMMENT 'WRAP 宿主绑定 JSON：forward/targetPath/probePath',
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
  `dsl_content` mediumtext COMMENT '逻辑编排 (FLOW) — Flow DSL JSON',
  `sql_content` text COMMENT '数据库 (DB) — SQL 脚本',
  `json_content` mediumtext COMMENT '静态 JSON (JSON) — JSON 内容',
  `text_content` text COMMENT '静态文本 (STRING) — 纯文本内容',
  `published_snapshot` mediumtext COMMENT '发布时的完整内容快照 (JSON)，运行时引擎从此字段读取',
  `publish_time` datetime COMMENT '最近一次发布时间',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志：1-开启，0-关闭',
  `log_mode` varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT' COMMENT '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录',
  `log_retention_days` int COMMENT '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数',
  `cache_config` text COMMENT '响应缓存配置 JSON：enabled/ttlSeconds/keyParams/includePageable',
  `security_config` text COMMENT '入站防护 JSON：authMode/antiReplay/rateLimit/ipAllowlist',
  `privacy_config` text COMMENT '出站隐私拦截 JSON：enabled/fieldSuffix/extraFields/mask',
  `view_export_config` text COMMENT '数据查看与导出 JSON：columns/sheetName/maxExportRows',
  `deleted` int DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，自动记录为当前时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_api_info_directory_id` (`directory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口配置类';

-- >>> flow_asset_version.sql
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

-- >>> flow_datasource.sql
-- Table: flow_datasource
-- 动态数据源配置表
CREATE TABLE IF NOT EXISTS `flow_datasource` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `code` varchar(50) COMMENT '数据源全局唯一编码，用于跨环境关联',
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
  `health_status` varchar(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '连接健康度：HEALTHY-健康, UNHEALTHY-异常, UNKNOWN-未知',
  `error_count` int NOT NULL DEFAULT 0 COMMENT '连续连接失败次数',
  `last_error_msg` text COMMENT '最后一次连接失败的异常堆栈/简述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_datasource_name` (`name`),
  UNIQUE KEY `uk_flow_datasource_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态数据源配置表';

-- >>> flow_directory.sql
-- Table: flow_directory
-- 全局目录表
CREATE TABLE IF NOT EXISTS `flow_directory` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `parent_id` varchar(32) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `biz_type` varchar(32) COMMENT '业务域：api/task/service/model/page，空=共用',
  `sort` int DEFAULT 0 COMMENT '排序（升序）',
  `path_prefix` varchar(256) DEFAULT NULL COMMENT 'URL路径前缀，可空；新建接口默认继承',
  `security_config` text COMMENT '目录级入站防护JSON，结构同ApiSecurityConfig',
  `privacy_config` text COMMENT '目录级出站隐私JSON，结构同ApiPrivacyConfig',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_directory_biz_type` (`biz_type`),
  KEY `idx_flow_directory_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局目录表';

-- >>> flow_env.sql
-- Table: flow_env
-- 发布逻辑环境
CREATE TABLE IF NOT EXISTS `flow_env` (
  `id` varchar(32) NOT NULL COMMENT '主键（固定码如 env_dev）',
  `code` varchar(32) NOT NULL COMMENT 'DEV|STAGING|PROD',
  `name` varchar(100) NOT NULL COMMENT '显示名',
  `require_suite_pass` tinyint NOT NULL DEFAULT 0 COMMENT '1=发布前需回归通过',
  `pass_ttl_hours` int NOT NULL DEFAULT 24 COMMENT '通过结果有效小时数',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用：0=否, 1=是',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `remark` varchar(512) COMMENT '备注',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_env_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布逻辑环境';

-- >>> flow_log_audit.sql
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

-- >>> flow_log_execution.sql
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
  `service_type` varchar(32) COMMENT '接口类型: FLOW/DB/JSON/STRING',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_execution_api_id` (`api_id`),
  KEY `idx_flow_log_execution_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API执行日志表';

-- >>> flow_log_login.sql
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

-- >>> flow_log_open_call.sql
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

-- >>> flow_log_service.sql
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

-- >>> flow_log_task.sql
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

-- >>> flow_log_third.sql
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

-- >>> flow_metrics_meta.sql
-- Table: flow_metrics_meta
-- 资产运行计量元数据
CREATE TABLE IF NOT EXISTS `flow_metrics_meta` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `asset_type` varchar(16) NOT NULL COMMENT 'API / TASK / MQ_TASK / SERVICE / PLATFORM / SYSTEM',
  `asset_id` varchar(32) NOT NULL COMMENT '资产ID',
  `last_success_at` bigint COMMENT '最近成功 epoch ms',
  `last_fail_at` bigint COMMENT '最近业务失败 epoch ms',
  `consec_fail` bigint NOT NULL DEFAULT 0 COMMENT '连续业务失败次数',
  `update_time` datetime COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_metrics_meta_asset_type` (`asset_type`),
  UNIQUE KEY `uk_flow_metrics_meta_asset_type_asset_id` (`asset_type`, `asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量元数据';

-- >>> flow_metrics_minute.sql
-- Table: flow_metrics_minute
-- 资产运行计量分钟汇总
CREATE TABLE IF NOT EXISTS `flow_metrics_minute` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `asset_type` varchar(16) NOT NULL COMMENT 'API / TASK / MQ_TASK / SERVICE / PLATFORM / SYSTEM',
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
  UNIQUE KEY `uk_flow_metrics_minute_atype_aid_ttype_bstart` (`asset_type`, `asset_id`, `trigger_type`, `bucket_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量分钟汇总';

-- >>> flow_model_directory.sql
-- Table: flow_model_directory
-- 数据模型目录表
CREATE TABLE IF NOT EXISTS `flow_model_directory` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `parent_id` varchar(32) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `sort` int DEFAULT 0 COMMENT '排序（升序）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_model_directory_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型目录表';

-- >>> flow_model_info.sql
-- Table: flow_model_info
-- 数据模型信息表
CREATE TABLE IF NOT EXISTS `flow_model_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `directory_id` varchar(32) COMMENT '关联全局目录树',
  `name` varchar(256) NOT NULL COMMENT '模型中文名，如：用户信息',
  `table_name` varchar(256) NOT NULL COMMENT '底层物理表名，如：t_user',
  `fields_schema` longtext COMMENT '核心元数据 JSON 数组（包含字段名、类型、UI配置等）',
  `status` tinyint DEFAULT 0 COMMENT '状态：0=停用，1=启用',
  `datasource` varchar(50) COMMENT '关联的动态数据源 code',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_model_info_directory_id` (`directory_id`),
  KEY `idx_flow_model_info_status` (`status`),
  UNIQUE KEY `uk_flow_model_info_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型信息表';

-- >>> flow_mq_connection.sql
-- Table: flow_mq_connection
-- MQ 连接配置（RabbitMQ / Kafka）
CREATE TABLE IF NOT EXISTS `flow_mq_connection` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '连接编码（未删除记录内唯一），流程 DSL / MQ 任务通过 code 引用',
  `name` varchar(128) NOT NULL COMMENT '连接名称',
  `mq_type` varchar(32) NOT NULL COMMENT 'MQ 类型：RABBITMQ / KAFKA',
  `servers` varchar(512) NOT NULL COMMENT '服务器地址 host:port，多个逗号分隔（Kafka 即 bootstrap.servers）',
  `virtual_host` varchar(128) COMMENT '虚拟主机（仅 RabbitMQ，默认 /）',
  `username` varchar(128) COMMENT '用户名（可空；Kafka 有值时启用 SASL/PLAIN）',
  `password` varchar(512) COMMENT '密码（AES 密文存储）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `health_status` varchar(32) COMMENT '健康状态：HEALTHY / UNHEALTHY / UNKNOWN',
  `last_error_msg` varchar(1024) COMMENT '最近一次连接测试错误信息',
  `last_test_time` datetime COMMENT '最近一次连接测试时间',
  `info` varchar(512) COMMENT '备注描述',
  `deleted` int NOT NULL DEFAULT 0 COMMENT '软删除：0=正常, 1=已删除',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_mq_connection_code` (`code`),
  KEY `idx_flow_mq_connection_enabled` (`enabled`),
  KEY `idx_flow_mq_connection_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 连接配置';

-- >>> flow_mq_task_info.sql
-- Table: flow_mq_task_info
-- MQ 任务定义（mqTrigger 入口流程资产）
CREATE TABLE IF NOT EXISTS `flow_mq_task_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '任务名称',
  `directory_id` varchar(32) COMMENT '关联目录ID（复用全局目录树）',
  `connection_code` varchar(64) NOT NULL COMMENT '绑定 MQ 连接编码（flow_mq_connection.code）',
  `topic` varchar(255) NOT NULL COMMENT '订阅 topic / 队列名',
  `consumer_group` varchar(128) COMMENT '消费组（Kafka group.id；Rabbit 忽略）',
  `concurrency` int NOT NULL DEFAULT 1 COMMENT '消费并发数',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
  `log_mode` varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT' COMMENT '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录',
  `log_payload_mode` varchar(16) DEFAULT 'SYSTEM_DEFAULT' COMMENT '原始报文落库策略：SYSTEM_DEFAULT/FULL/MASK/OFF',
  `log_retention_days` int COMMENT '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数',
  `retry_max` int DEFAULT 0 COMMENT '失败重试次数（0=不重试）',
  `retry_backoff_ms` int DEFAULT 1000 COMMENT '重试间隔毫秒',
  `dead_letter_topic` varchar(255) DEFAULT NULL COMMENT '最终失败时转发的死信 topic/队列',
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
  KEY `idx_flow_mq_task_info_create_time` (`create_time`),
  KEY `idx_flow_mq_task_info_directory_id` (`directory_id`),
  KEY `idx_flow_mq_task_info_enabled` (`enabled`),
  KEY `idx_flow_mq_task_info_publish_status` (`publish_status`),
  KEY `idx_flow_mq_task_info_connection_code` (`connection_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 任务定义';

-- >>> flow_mq_task_log.sql
-- Table: flow_mq_task_log
-- MQ 任务执行日志
CREATE TABLE IF NOT EXISTS `flow_mq_task_log` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `task_id` varchar(32) NOT NULL COMMENT '关联 MQ 任务ID',
  `task_name` varchar(128) COMMENT '任务名称（冗余）',
  `topic` varchar(255) COMMENT '消息 topic / 队列名',
  `message_id` varchar(255) COMMENT '消息ID（幂等去重键）',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'MQ' COMMENT '触发类型：MQ=消息触发, MANUAL=手动',
  `status` varchar(16) NOT NULL COMMENT '执行状态：SUCCESS / FAILED / SKIPPED / RUNNING',
  `cost_time_ms` bigint COMMENT '耗时（毫秒）',
  `error_msg` text COMMENT '失败信息',
  `message_body` longtext COMMENT '原始消息体（JSON 解析前的字符串，支持超限截断）',
  `message_headers` text COMMENT '消息头 JSON 字典',
  `trace_data` longtext COMMENT 'FlowTrace JSON 快照（logMode=ALL 时记录）',
  `create_time` datetime COMMENT '执行开始时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_mq_task_log_create_time` (`create_time`),
  KEY `idx_flow_mq_task_log_status` (`status`),
  KEY `idx_flow_mq_task_log_task_id` (`task_id`),
  KEY `idx_flow_mq_task_log_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 任务执行日志';

-- >>> flow_open_api_grant.sql
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

-- >>> flow_open_credential.sql
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

-- >>> flow_open_platform.sql
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

-- >>> flow_oss_connection.sql
-- Table: flow_oss_connection
-- MinIO / S3 兼容对象存储连接配置
CREATE TABLE IF NOT EXISTS `flow_oss_connection` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '连接编码（未删除记录内唯一）',
  `name` varchar(128) NOT NULL COMMENT '连接名称',
  `endpoint` varchar(512) NOT NULL COMMENT 'MinIO endpoint，如 http://minio:9000',
  `access_key` varchar(128) COMMENT 'Access Key',
  `secret_key` varchar(512) COMMENT 'Secret Key（AES 密文）',
  `region` varchar(64) COMMENT '区域（可空）',
  `path_style` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否 path-style 访问',
  `public_bucket` varchar(128) COMMENT '公有桶名',
  `private_bucket` varchar(128) COMMENT '私有桶名',
  `public_base_url` varchar(512) COMMENT '公有访问前缀（Nginx 对外 URL）',
  `key_prefix` varchar(256) COMMENT '对象键强制前缀',
  `public_access_mode` varchar(32) DEFAULT 'NGINX_PROXY' COMMENT 'ANON / NGINX_PROXY',
  `private_download_mode` varchar(16) NOT NULL DEFAULT 'STREAM' COMMENT '隐私下载：STREAM / PRESIGN',
  `presign_expire_seconds` int NOT NULL DEFAULT 300 COMMENT '预签名有效期（秒）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态',
  `health_status` varchar(32) COMMENT 'HEALTHY / UNHEALTHY / UNKNOWN',
  `last_error_msg` varchar(1024) COMMENT '最近测试错误',
  `last_test_time` datetime COMMENT '最近测试时间',
  `info` varchar(512) COMMENT '备注',
  `deleted` int NOT NULL DEFAULT 0 COMMENT '软删除',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_oss_connection_code` (`code`),
  KEY `idx_flow_oss_connection_enabled` (`enabled`),
  KEY `idx_flow_oss_connection_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 连接配置';

-- >>> flow_oss_download_log.sql
-- Table: flow_oss_download_log
-- OSS 隐私下载审计
CREATE TABLE IF NOT EXISTS `flow_oss_download_log` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `object_id` varchar(32) COMMENT '台账 ID',
  `downloaded_by` varchar(64) COMMENT '下载人 userId',
  `downloaded_by_name` varchar(128) COMMENT '下载人展示名',
  `client_ip` varchar(64) COMMENT '客户端 IP',
  `user_agent` varchar(512) COMMENT 'User-Agent',
  `result` varchar(16) COMMENT 'SUCCESS / DENIED / NOT_FOUND / ERROR',
  `deny_reason` varchar(512) COMMENT '拒绝原因',
  `time_ms` bigint COMMENT '耗时毫秒',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_oss_download_log_object_id` (`object_id`),
  KEY `idx_flow_oss_download_log_create_time` (`create_time`),
  KEY `idx_flow_oss_download_log_result` (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 隐私下载审计';

-- >>> flow_oss_object_ref.sql
-- Table: flow_oss_object_ref
-- 对象存储业务引用
CREATE TABLE IF NOT EXISTS `flow_oss_object_ref` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `object_id` varchar(32) NOT NULL COMMENT '台账ID',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_id` varchar(128) NOT NULL COMMENT '业务单据ID',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_oss_object_ref_object_id_biz_type_biz_id` (`object_id`, `biz_type`, `biz_id`),
  KEY `idx_flow_oss_object_ref_object_id` (`object_id`),
  KEY `idx_flow_oss_object_ref_biz_type_biz_id` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储业务引用';

-- >>> flow_oss_object.sql
-- Table: flow_oss_object
-- OSS 文件台账（元数据在库，字节在 MinIO）
CREATE TABLE IF NOT EXISTS `flow_oss_object` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID（隐私下载主键）',
  `profile_code` varchar(64) COMMENT '上传场景编码',
  `connection_code` varchar(64) COMMENT '连接编码',
  `bucket` varchar(128) COMMENT '桶名',
  `object_key` varchar(1024) COMMENT '对象键',
  `visibility` varchar(16) COMMENT 'PUBLIC / PRIVATE',
  `public_path` varchar(1024) COMMENT '公有相对路径',
  `original_name` varchar(512) COMMENT '原始文件名',
  `content_type` varchar(128) COMMENT 'Content-Type',
  `extension` varchar(32) COMMENT '扩展名',
  `size_bytes` bigint COMMENT '文件大小',
  `checksum_sha256` varchar(64) COMMENT 'SHA-256',
  `biz_meta` text COMMENT '业务扩展 JSON',
  `uploaded_by` varchar(64) COMMENT '上传人 userId（各用户体系内主键，不带类型前缀）',
  `uploaded_by_user_type` varchar(32) COMMENT '上传人 userType：ADMIN / END_USER / OPEN_APP（宿主可扩展）',
  `uploaded_by_name` varchar(128) COMMENT '上传人展示名',
  `dept_id` varchar(64) COMMENT '部门 ID（数据权限）',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PENDING（预签名待确认）/ DELETED',
  `expires_at` datetime DEFAULT NULL COMMENT '临时文件过期时间，空=不过期',
  `object_purged` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'MinIO 对象是否已物理删除：0=否, 1=是',
  `thumb_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE / PENDING / READY / FAILED / SKIPPED',
  `thumb_object_key` varchar(1024) DEFAULT NULL COMMENT '缩略图对象键',
  `thumb_public_path` varchar(1024) DEFAULT NULL COMMENT '缩略图公有路径',
  `thumb_content_type` varchar(128) DEFAULT NULL COMMENT '缩略图 Content-Type',
  `thumb_size_bytes` bigint DEFAULT NULL COMMENT '缩略图大小',
  `thumb_error` varchar(512) DEFAULT NULL COMMENT '缩略图失败原因',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_oss_object_profile_code` (`profile_code`),
  KEY `idx_flow_oss_object_uploaded_by_uploaded_by_user_type` (`uploaded_by`, `uploaded_by_user_type`),
  KEY `idx_flow_oss_object_dept_id` (`dept_id`),
  KEY `idx_flow_oss_object_status` (`status`),
  KEY `idx_flow_oss_object_create_time` (`create_time`),
  KEY `idx_flow_oss_object_expires_at` (`expires_at`),
  KEY `idx_flow_oss_object_status_object_purged` (`status`, `object_purged`),
  KEY `idx_flow_oss_object_thumb_status` (`thumb_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 文件台账';

-- >>> flow_oss_upload_profile.sql
-- Table: flow_oss_upload_profile
-- OSS 上传场景（业务 Profile）
CREATE TABLE IF NOT EXISTS `flow_oss_upload_profile` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '场景编码（未删除记录内唯一）',
  `name` varchar(128) NOT NULL COMMENT '场景名称',
  `connection_code` varchar(64) NOT NULL COMMENT '绑定 OSS 连接编码',
  `visibility` varchar(16) NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC / PRIVATE',
  `bucket_override` varchar(128) COMMENT '桶覆盖（可空）',
  `key_pattern` varchar(512) COMMENT '对象键 pattern',
  `allowed_content_types` text COMMENT 'Content-Type 白名单，逗号分隔',
  `allowed_extensions` varchar(512) COMMENT '扩展名白名单，逗号分隔',
  `max_size_bytes` bigint COMMENT '单文件大小上限（字节）',
  `max_files_per_request` int DEFAULT 1 COMMENT '单次最多文件数',
  `quota_max_bytes` bigint COMMENT '场景容量配额（字节），空=不限',
  `quota_max_files` int COMMENT '场景文件数配额，空=不限',
  `thumbnail_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否异步生成缩略图：0=否, 1=是',
  `thumbnail_max_edge` int DEFAULT NULL COMMENT '缩略图最长边像素，空=用全局',
  `thumbnail_max_source_bytes` bigint DEFAULT NULL COMMENT '参与缩略图的源文件上限，空=用全局',
  `thumbnail_jpeg_quality` decimal(3,2) DEFAULT NULL COMMENT 'JPEG 质量 0~1，空=用全局',
  `require_auth` tinyint(1) NOT NULL DEFAULT 1 COMMENT '上传是否必须登录',
  `presign_upload_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否开放预签名直传：0=仅网关代理上传, 1=允许客户端 PUT 直达 OSS',
  `biz_fields_schema` text COMMENT '业务字段 JSON Schema',
  `upload_perm` varchar(128) DEFAULT NULL COMMENT '上传权限码：哪些 RBAC 权限才能调用该场景的上传 API；留空=仅 require_auth 控制',
  `download_perm` varchar(128) DEFAULT NULL COMMENT '下载权限码：哪些 RBAC 权限可突破 DataScope 访问私有文件；留空=仅 DataScope 控制',
  `caller_policy` text COMMENT '访问规则 JSON：{"rules":[OssAccessRule]}',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态',
  `remark` varchar(512) COMMENT '备注',
  `deleted` int NOT NULL DEFAULT 0 COMMENT '软删除',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_oss_upload_profile_code` (`code`),
  KEY `idx_flow_oss_upload_profile_connection_code` (`connection_code`),
  KEY `idx_flow_oss_upload_profile_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 上传场景';

-- >>> flow_page_directory.sql
-- Table: flow_page_directory
-- 页面目录表
CREATE TABLE IF NOT EXISTS `flow_page_directory` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `parent_id` varchar(32) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `sort` int DEFAULT 0 COMMENT '排序（升序）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_page_directory_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面目录表';

-- >>> flow_page_info.sql
-- Table: flow_page_info
-- 页面信息表
CREATE TABLE IF NOT EXISTS `flow_page_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `directory_id` varchar(32) COMMENT '关联全局目录树',
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

-- >>> flow_regression_case.sql
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

-- >>> flow_regression_run_case.sql
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

-- >>> flow_regression_run.sql
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

-- >>> flow_regression_suite.sql
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

-- >>> flow_response_template.sql
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

-- >>> flow_service_info.sql
-- Table: flow_service_info
-- 内部服务编排定义
CREATE TABLE IF NOT EXISTS `flow_service_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '服务名称',
  `directory_id` varchar(32) COMMENT '关联目录ID（复用全局目录树）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
  `log_mode` varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT' COMMENT '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录',
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

-- >>> flow_sys_config.sql
-- Table: flow_sys_config
-- 系统配置表 (System Configuration)
CREATE TABLE IF NOT EXISTS `flow_sys_config` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
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

-- >>> flow_sys_macro.sql
-- Table: flow_sys_macro
-- 系统全局宏定义字典表 (Semantic Layer)
CREATE TABLE IF NOT EXISTS `flow_sys_macro` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `macro_code` varchar(100) NOT NULL COMMENT '宏编码 (前端调用的唯一凭证，如: sys_user_id)',
  `macro_name` varchar(100) NOT NULL COMMENT '宏名称 (如: 当前登录用户 ID)',
  `macro_type` varchar(50) NOT NULL COMMENT '宏类型 (枚举: VARIABLE 变量, FUNCTION 方法)',
  `expression` varchar(500) NOT NULL COMMENT '真实的 SpEL 表达式 (如: @userContext.getUserId())',
  `scope` varchar(50) NOT NULL DEFAULT 'ALL' COMMENT '作用域 (枚举: ALL 全局, SQL_ONLY 仅SQL, JS_ONLY 仅JS)',
  `return_type` varchar(50) COMMENT '返回值类型 (用于前端 JS 类型推导提示，如 String, Number)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态 (1: 启用, 0: 停用)',
  `remark` varchar(500) COMMENT '备注说明',
  `macro_params` varchar(255) COMMENT '入参列表 (仅 FUNCTION 类型有效，逗号分隔，如 date,format)',
  `create_by` varchar(64) COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_macro_macro_code` (`macro_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统全局宏定义字典表 (Semantic Layer)';

-- >>> flow_sys_permission.sql
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

-- >>> flow_sys_role_permission.sql
-- Table: flow_sys_role_permission
-- 角色-权限
CREATE TABLE IF NOT EXISTS `flow_sys_role_permission` (
  `role_id` varchar(32) NOT NULL,
  `perm_code` varchar(128) NOT NULL,
  PRIMARY KEY (`role_id`, `perm_code`),
  KEY `idx_flow_sys_role_permission_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';

-- >>> flow_sys_role.sql
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

-- >>> flow_sys_user_role.sql
-- Table: flow_sys_user_role
-- 用户-角色
CREATE TABLE IF NOT EXISTS `flow_sys_user_role` (
  `user_id` varchar(32) NOT NULL,
  `role_id` varchar(32) NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_flow_sys_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

-- >>> flow_sys_user.sql
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

-- >>> flow_task_info.sql
-- Table: flow_task_info
-- 定时任务定义
CREATE TABLE IF NOT EXISTS `flow_task_info` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '任务名称',
  `directory_id` varchar(32) COMMENT '关联目录ID（复用全局目录树）',
  `cron` varchar(64) NOT NULL COMMENT 'Cron 表达式，如 0/5 * * * * ?',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
  `log_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
  `log_mode` varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT' COMMENT '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录',
  `log_retention_days` int COMMENT '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数',
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

