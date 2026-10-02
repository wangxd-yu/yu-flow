-- 基线 2026-10-02，冻结。之后的结构变更另写 V 脚本，并同步 sql-*/ 单表与 00_all。
-- 由 00_all_flow_tables.sql 与 00_system_init.sql 拼成，不要手改。

-- Yu Flow MySQL 全量建表（由 sql-mysql/flow_*.sql 汇总生成，勿手工穿插重复表）
-- 生成时间: 2026-09-28T01:46:19.243Z
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
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
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

-- >>> flow_db_connection.sql
-- Table: flow_db_connection
-- JDBC 数据库连接配置（原 flow_datasource）
CREATE TABLE IF NOT EXISTS `flow_db_connection` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `code` varchar(50) COMMENT '连接全局唯一编码，用于跨环境关联',
  `name` varchar(100) NOT NULL COMMENT '连接名称',
  `db_type` varchar(20) NOT NULL COMMENT '数据库类型(mysql/postgresql/highgo)',
  `driver_class_name` varchar(200) NOT NULL COMMENT '驱动类名',
  `url` varchar(500) NOT NULL COMMENT 'JDBC URL',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(512) NOT NULL COMMENT '密码（AES 密文）',
  `initial_size` int DEFAULT 5 COMMENT '初始连接数',
  `min_idle` int DEFAULT 5 COMMENT '最小空闲连接',
  `max_active` int DEFAULT 20 COMMENT '最大活动连接',
  `status` tinyint DEFAULT 1 COMMENT '状态(0-停用,1-启用)',
  `wall_config` text COMMENT 'SQL安全墙JSON(DataSourceWallConfig)',
  `is_system` tinyint NOT NULL DEFAULT 0 COMMENT '系统连接(1=不可删改连接，如[DEFAULT])',
  `health_status` varchar(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '连接健康度：HEALTHY-健康, UNHEALTHY-异常, UNKNOWN-未知',
  `error_count` int NOT NULL DEFAULT 0 COMMENT '连续连接失败次数',
  `last_error_msg` text COMMENT '最后一次连接失败的异常堆栈/简述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_db_connection_name` (`name`),
  UNIQUE KEY `uk_flow_db_connection_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JDBC 数据库连接配置';

-- >>> flow_directory.sql
-- Table: flow_directory
-- 全局目录表
CREATE TABLE IF NOT EXISTS `flow_directory` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `parent_id` varchar(32) COMMENT '父节点ID，NULL 表示根节点',
  `name` varchar(128) NOT NULL COMMENT '目录名称',
  `biz_type` varchar(32) COMMENT '业务域：api/task/service/model/page/mqtask，空=共用',
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

-- >>> flow_log_alert.sql
-- Table: flow_log_alert
-- 告警事件历史
CREATE TABLE IF NOT EXISTS `flow_log_alert` (
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
  KEY `idx_flow_log_alert_fired_at` (`fired_at`),
  KEY `idx_flow_log_alert_rule_id` (`rule_id`),
  KEY `idx_flow_log_alert_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件历史';

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

-- >>> flow_log_mq_task.sql
-- Table: flow_log_mq_task
-- MQ 任务执行日志
CREATE TABLE IF NOT EXISTS `flow_log_mq_task` (
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
  KEY `idx_flow_log_mq_task_create_time` (`create_time`),
  KEY `idx_flow_log_mq_task_status` (`status`),
  KEY `idx_flow_log_mq_task_task_id` (`task_id`),
  KEY `idx_flow_log_mq_task_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 任务执行日志';

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

-- >>> flow_log_oss_download.sql
-- Table: flow_log_oss_download
-- OSS 隐私下载审计
CREATE TABLE IF NOT EXISTS `flow_log_oss_download` (
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
  KEY `idx_flow_log_oss_download_object_id` (`object_id`),
  KEY `idx_flow_log_oss_download_create_time` (`create_time`),
  KEY `idx_flow_log_oss_download_result` (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 隐私下载审计';

-- >>> flow_log_release_import.sql
-- Table: flow_log_release_import
-- 发布包导入记录（含导入前备份，用于一键回滚）
CREATE TABLE IF NOT EXISTS `flow_log_release_import` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `release_code` varchar(64) COMMENT '版本号',
  `release_name` varchar(128) COMMENT '版本名称',
  `package_digest` varchar(64) COMMENT '发布包 manifest SHA-256，可与来源环境版本单对账',
  `source_env` varchar(32) COMMENT '来源环境',
  `target_env` varchar(32) COMMENT '目标环境（本实例）',
  `status` varchar(16) NOT NULL COMMENT '状态：SUCCESS / FAILED / ROLLED_BACK',
  `summary` varchar(512) COMMENT '摘要',
  `error_message` varchar(2000) COMMENT '失败原因',
  `report_json` mediumtext COMMENT '导入报告 JSON',
  `backup_json` longtext COMMENT '导入前受影响资产的完整状态 JSON（回滚依据）',
  `asset_hashes` mediumtext COMMENT '导入后各资产内容指纹 JSON（类型:ID → 指纹），用于发现生产被直接修改',
  `runtime_issues` text COMMENT '导入提交后的运行时自检问题（JSON 字符串数组），为空表示自检通过',
  `imported_by` varchar(64) COMMENT '导入人',
  `imported_time` datetime COMMENT '导入时间',
  `rolled_back_by` varchar(64) COMMENT '回滚人',
  `rolled_back_time` datetime COMMENT '回滚时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_release_import_imported_time` (`imported_time`),
  KEY `idx_flow_log_release_import_package_digest` (`package_digest`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布包导入记录';

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
  `parent_object_id` varchar(32) DEFAULT NULL COMMENT '来源压缩包台账 ID，空=独立上传',
  `archive_entry_path` varchar(512) DEFAULT NULL COMMENT '包内相对路径（正斜杠）',
  `extract_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE / PENDING / EXTRACTING / DONE / FAILED / SKIPPED',
  `extract_error` varchar(512) DEFAULT NULL COMMENT '展开结果摘要或失败原因',
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
  KEY `idx_flow_oss_object_thumb_status` (`thumb_status`),
  KEY `idx_flow_oss_object_parent_object_id` (`parent_object_id`),
  KEY `idx_flow_oss_object_extract_status` (`extract_status`)
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
  `extract_archive_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '上传 zip 后是否异步展开：0=否, 1=是',
  `extract_keep_archive` tinyint(1) NOT NULL DEFAULT 1 COMMENT '展开成功后是否保留原包：1=保留, 0=软删原包',
  `extract_reject_policy` varchar(32) NOT NULL DEFAULT 'SKIP_ZERO_FAIL' COMMENT '不合格条目：SKIP_ZERO_FAIL=跳过且0合格则失败 / FAIL_PACK=任一不合格整包失败',
  `extract_allowed_extensions` varchar(512) COMMENT '展开后落库扩展名白名单，空=不限制（仍排除嵌套压缩包）',
  `extract_allowed_content_types` text COMMENT '展开后落库 MIME 白名单，空=不限制',
  `extract_max_entries` int DEFAULT NULL COMMENT '单包最多处理条目数，空=用全局',
  `extract_max_uncompressed_bytes` bigint DEFAULT NULL COMMENT '单包解压后总字节上限，空=用全局',
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

-- >>> flow_release_item.sql
-- Table: flow_release_item
-- 版本单明细
CREATE TABLE IF NOT EXISTS `flow_release_item` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `release_id` varchar(32) NOT NULL COMMENT '版本单ID（flow_release.id）',
  `asset_type` varchar(32) NOT NULL COMMENT '资产类型：API / SERVICE / TASK / MQ_TASK / RESPONSE_TEMPLATE / PAGE / MODEL / SYS_MACRO / SYS_CONFIG / OPEN_PLATFORM / ALERT_RULE',
  `asset_id` varchar(64) NOT NULL COMMENT '资产ID',
  `asset_name` varchar(255) COMMENT '资产名称（冗余，资产删除后仍可显示）',
  `asset_key` varchar(128) COMMENT '按编码匹配的类型（全局宏/系统配置/开放平台）在目标环境的匹配键',
  `action` varchar(16) NOT NULL COMMENT '动作：UPSERT 新增或更新 / OFFLINE 下线',
  `origin` varchar(16) NOT NULL COMMENT '来源：MANUAL 手工加入 / DEPENDENCY 依赖补齐 / SCAN 变更扫描',
  `content_hash` varchar(64) COMMENT '冻结时的内容指纹（SHA-256），冻结后内容变化即视为漂移',
  `create_by` varchar(64) COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_release_item_release_id_atype_aid` (`release_id`, `asset_type`, `asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本单明细';

-- >>> flow_release.sql
-- Table: flow_release
-- 版本单：一轮上线要带到生产的资产清单
CREATE TABLE IF NOT EXISTS `flow_release` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '版本号（全局唯一，如 v2026.10）',
  `name` varchar(128) COMMENT '版本名称',
  `status` varchar(16) NOT NULL COMMENT '状态：DRAFT 编辑中 / FROZEN 已冻结 / EXPORTED 已导出',
  `remark` text COMMENT '发布说明（导出时写入包内 CHANGELOG.md）',
  `source_env` varchar(32) COMMENT '创建时的实例环境（flow_env.code）',
  `frozen_by` varchar(64) COMMENT '冻结人',
  `frozen_time` datetime COMMENT '冻结时间',
  `exported_by` varchar(64) COMMENT '最近导出人',
  `exported_time` datetime COMMENT '最近导出时间',
  `package_digest` varchar(64) COMMENT '最近导出包的 manifest SHA-256，用于与生产导入记录对账',
  `create_by` varchar(64) COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新人',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_release_code` (`code`),
  KEY `idx_flow_release_status` (`status`),
  KEY `idx_flow_release_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本单';

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

-- >>> flow_sys_env_variable.sql
-- Table: flow_sys_env_variable
-- 环境变量（每个环境各自维护，值不随发布包迁移）
CREATE TABLE IF NOT EXISTS `flow_sys_env_variable` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '变量名（大写字母开头，仅大写字母/数字/下划线），编排中以 $.env.CODE / ${env.CODE} 引用',
  `var_value` varchar(4000) COMMENT '变量值；secret=1 时为 AES 密文',
  `secret` tinyint(1) NOT NULL DEFAULT 0 COMMENT '敏感变量：0=否, 1=是（页面掩码显示，执行轨迹与三方日志脱敏）',
  `remark` varchar(512) COMMENT '说明（随发布包导出，提示目标环境该填什么）',
  `create_by` varchar(64) COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新人',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_env_variable_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境变量';

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

-- ============================================================================
-- Yu Flow 系统种子数据（MySQL）
-- 来源：本地 flow 库导出；幂等 ON DUPLICATE KEY UPDATE
-- 用法：先执行 00_all_flow_tables.sql，再执行本文件
-- 取代：flow_sys_config_init.sql / flow_sys_macro_init.sql（可保留作参考）
-- 管理员账号：不写用户表，启动时由 RbacAdminBootstrap + yu.flow.* 引导
-- 注意：flow_db_connection 含本机 JDBC URL，部署时请按环境修改或删掉该段
-- ============================================================================

-- ===== flow_sys_role (3) =====

INSERT INTO `flow_sys_role` (`id`, `role_code`, `role_name`, `status`, `is_builtin`, `remark`, `create_time`, `update_time`) VALUES
('role_admin', 'ADMIN', '管理员', 1, 1, '全部权限', '2026-07-22 20:52:46', '2026-07-22 20:52:46'),
('role_operator', 'OPERATOR', '运维员', 1, 1, '编排与观测，无用户/系统配置写', '2026-07-22 20:52:46', '2026-07-22 20:52:46'),
('role_viewer', 'VIEWER', '只读员', 1, 1, '只读查看', '2026-07-22 20:52:46', '2026-07-22 20:52:46'),
('role_deployer', 'DEPLOYER', '运维实施', 1, 1, '目标环境导入发布包、回滚、维护环境变量、数据源与连接配置；不能直接修改编排资产', '2026-09-27 23:59:00', '2026-09-27 23:59:00')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_sys_permission (40) =====

INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`) VALUES
('p_alert_v', 'flow:alert:view', '告警查看', 'ops', NULL, '2026-07-22 22:19:49'),
('p_alert_w', 'flow:alert:edit', '告警管理', 'ops', NULL, '2026-07-22 22:19:49'),
('p_all', '*', '全部权限', 'sys', 'ADMIN 超权', '2026-07-22 20:52:46'),
('p_api_v', 'flow:api:view', '接口查看', 'flow', NULL, '2026-07-22 20:52:46'),
('p_api_w', 'flow:api:write', '接口编排', 'flow', NULL, '2026-07-22 20:52:46'),
('p_cfg_v', 'sys:config:view', '系统配置查看', 'sys', NULL, '2026-07-22 20:52:46'),
('p_cfg_w', 'sys:config:write', '系统配置管理', 'sys', NULL, '2026-07-22 20:52:46'),
('p_conn_w', 'flow:conn:write', '连接配置维护', 'infra', 'MQ 连接、OSS 连接、告警通道的新增与修改（不含 MQ 任务、存储场景、告警规则）', '2026-09-28 09:00:00'),
('p_docs', 'docs:view', '接口文档', 'sys', NULL, '2026-07-22 20:52:46'),
('p_ds_v', 'flow:ds:view', '数据源查看', 'infra', NULL, '2026-07-22 20:52:46'),
('p_ds_w', 'flow:ds:write', '数据源管理', 'infra', NULL, '2026-07-22 20:52:46'),
('p_env_v', 'sys:env:view', '环境变量查看', 'sys', '平台设置 · 环境变量', '2026-09-27 23:40:00'),
('p_env_w', 'sys:env:write', '环境变量管理', 'sys', '平台设置 · 环境变量', '2026-09-27 23:40:00'),
('p_home', 'home:view', '首页', 'home', NULL, '2026-07-22 20:52:46'),
('p_host_v', 'sys:host:view', '宿主机配置查看', 'sys', '平台设置 · 宿主机配置', '2026-08-13 16:00:00'),
('p_host_w', 'sys:host:write', '宿主机配置管理', 'sys', '平台设置 · 宿主机配置', '2026-08-13 16:00:00'),
('p_log_v', 'log:view', '日志中心', 'ops', NULL, '2026-07-22 20:52:46'),
('p_macro_v', 'sys:macro:view', '全局参数查看', 'sys', NULL, '2026-07-22 20:52:46'),
('p_macro_w', 'sys:macro:write', '全局参数管理', 'sys', NULL, '2026-07-22 20:52:46'),
('p_model_v', 'flow:model:view', '模型查看', 'infra', NULL, '2026-07-22 20:52:46'),
('p_model_w', 'flow:model:write', '模型管理', 'infra', NULL, '2026-07-22 20:52:46'),
('p_mq_v', 'flow:mq:view', 'MQ查看', 'flow', 'MQ 连接与 MQ 任务查看', '2026-08-01 10:16:35'),
('p_mq_w', 'flow:mq:write', 'MQ编排', 'flow', 'MQ 连接与 MQ 任务编辑/发布/启停', '2026-08-01 10:16:35'),
('p_open_v', 'flow:open:view', '开放平台查看', 'ops', NULL, '2026-07-22 20:52:46'),
('p_open_w', 'flow:open:write', '开放平台管理', 'ops', NULL, '2026-07-22 20:52:46'),
('p_oss_a', 'flow:oss:admin', '对象存储管理', 'flow', '跨用户台账与隐私下载（内置 Scope=ALL）', '2026-08-03 19:35:29'),
('p_oss_t', 'flow:oss:audit', '对象存储审计', 'flow', '隐私下载审计日志', '2026-08-03 19:35:29'),
('p_oss_v', 'flow:oss:view', '对象存储查看', 'flow', 'OSS 连接/场景/台账查看', '2026-08-03 19:35:29'),
('p_oss_w', 'flow:oss:write', '对象存储编排', 'flow', 'OSS 连接/场景编辑', '2026-08-03 19:35:29'),
('p_page_v', 'flow:page:view', '页面查看', 'flow', NULL, '2026-07-22 20:52:46'),
('p_page_w', 'flow:page:write', '页面设计', 'flow', NULL, '2026-07-22 20:52:46'),
('p_privacy_r', 'flow:privacy:reveal', '隐私字段明文预览', 'flow', '控制台数据查看/预览明文逃生口；不替代宿主角色', '2026-08-14 14:00:00'),
('p_release_v', 'flow:release:view', '发布门禁查看', 'ops', NULL, '2026-07-24 11:28:05'),
('p_relimp', 'flow:release:import', '发布包导入', 'ops', '目标环境导入发布包（导入即发布）', '2026-09-27 23:59:00'),
('p_release_w', 'flow:release:edit', '发布门禁管理', 'ops', NULL, '2026-07-24 11:28:05'),
('p_relpkg_v', 'flow:release:pkg:view', '版本单查看', 'ops', '版本发布 · 版本单', '2026-09-27 23:55:00'),
('p_relpkg_w', 'flow:release:pkg:edit', '版本单管理', 'ops', '版本发布 · 维护/冻结/导出发布包', '2026-09-27 23:55:00'),
('p_relrb', 'flow:release:rollback', '导入回滚', 'ops', '回滚最近一次发布包导入', '2026-09-27 23:59:00'),
('p_role_v', 'sys:role:view', '角色查看', 'sys', NULL, '2026-07-22 21:32:58'),
('p_role_w', 'sys:role:write', '角色管理', 'sys', NULL, '2026-07-22 21:32:58'),
('p_rt_v', 'flow:runtime:view', '运行中心', 'ops', NULL, '2026-07-22 20:52:46'),
('p_svc_v', 'flow:service:view', '服务查看', 'flow', NULL, '2026-07-22 20:52:46'),
('p_svc_w', 'flow:service:write', '服务编排', 'flow', NULL, '2026-07-22 20:52:46'),
('p_task_v', 'flow:task:view', '任务查看', 'flow', NULL, '2026-07-22 20:52:46'),
('p_task_w', 'flow:task:write', '任务编排', 'flow', NULL, '2026-07-22 20:52:46'),
('p_tpl_v', 'sys:template:view', '响应模板查看', 'sys', NULL, '2026-07-22 20:52:46'),
('p_tpl_w', 'sys:template:write', '响应模板管理', 'sys', NULL, '2026-07-22 20:52:46'),
('p_user_v', 'sys:user:view', '用户查看', 'sys', NULL, '2026-07-22 20:52:46'),
('p_user_w', 'sys:user:write', '用户管理', 'sys', NULL, '2026-07-22 20:52:46')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_sys_role_permission (50) =====

INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`) VALUES
('role_admin', '*'),
('role_deployer', 'home:view'),
('role_deployer', 'flow:release:pkg:view'),
('role_deployer', 'flow:release:import'),
('role_deployer', 'flow:release:rollback'),
('role_deployer', 'sys:env:view'),
('role_deployer', 'sys:env:write'),
('role_deployer', 'flow:ds:view'),
('role_deployer', 'flow:ds:write'),
('role_deployer', 'flow:mq:view'),
('role_deployer', 'flow:oss:view'),
('role_deployer', 'flow:alert:view'),
('role_deployer', 'flow:conn:write'),
('role_deployer', 'flow:api:view'),
('role_deployer', 'flow:service:view'),
('role_deployer', 'flow:task:view'),
('role_deployer', 'flow:runtime:view'),
('role_deployer', 'log:view'),
('role_operator', 'docs:view'),
('role_operator', 'flow:alert:edit'),
('role_operator', 'flow:alert:view'),
('role_operator', 'flow:api:view'),
('role_operator', 'flow:api:write'),
('role_operator', 'flow:ds:view'),
('role_operator', 'flow:ds:write'),
('role_operator', 'flow:model:view'),
('role_operator', 'flow:model:write'),
('role_operator', 'flow:mq:view'),
('role_operator', 'flow:mq:write'),
('role_operator', 'flow:open:view'),
('role_operator', 'flow:open:write'),
('role_operator', 'flow:oss:view'),
('role_operator', 'flow:oss:write'),
('role_operator', 'flow:page:view'),
('role_operator', 'flow:page:write'),
('role_operator', 'flow:release:edit'),
('role_operator', 'flow:release:pkg:edit'),
('role_operator', 'flow:release:pkg:view'),
('role_operator', 'flow:release:view'),
('role_operator', 'flow:runtime:view'),
('role_operator', 'flow:service:view'),
('role_operator', 'flow:service:write'),
('role_operator', 'flow:task:view'),
('role_operator', 'flow:task:write'),
('role_operator', 'home:view'),
('role_operator', 'log:view'),
('role_operator', 'sys:config:view'),
('role_operator', 'sys:env:view'),
('role_operator', 'sys:host:view'),
('role_operator', 'sys:macro:view'),
('role_operator', 'sys:macro:write'),
('role_operator', 'sys:template:view'),
('role_operator', 'sys:template:write'),
('role_viewer', 'docs:view'),
('role_viewer', 'flow:alert:view'),
('role_viewer', 'flow:api:view'),
('role_viewer', 'flow:ds:view'),
('role_viewer', 'flow:model:view'),
('role_viewer', 'flow:mq:view'),
('role_viewer', 'flow:open:view'),
('role_viewer', 'flow:oss:view'),
('role_viewer', 'flow:page:view'),
('role_viewer', 'flow:release:pkg:view'),
('role_viewer', 'flow:release:view'),
('role_viewer', 'flow:runtime:view'),
('role_viewer', 'flow:service:view'),
('role_viewer', 'flow:task:view'),
('role_viewer', 'home:view'),
('role_viewer', 'log:view'),
('role_viewer', 'sys:config:view'),
('role_viewer', 'sys:env:view'),
('role_viewer', 'sys:host:view'),
('role_viewer', 'sys:macro:view'),
('role_viewer', 'sys:template:view')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);


-- ===== flow_sys_config (55) =====

INSERT INTO `flow_sys_config` (`id`, `config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `sort_order`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('1', 'SYSTEM_PREFIX', '/flow-api', 'STRING', 'GATEWAY', '网关 API 统一前缀，影响所有动态 API 的路由注册路径', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-04-12 19:29:32'),
('2', 'API_TIMEOUT', '30000', 'NUMBER', 'GATEWAY', 'API 请求超时时间（毫秒），超时后自动中断并返回 504', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-05-22 19:08:06'),
('3', 'TOKEN_EXPIRE', '14400', 'NUMBER', 'SECURITY', 'JWT Token 过期时间（秒），默认 2 小时', 1, 1, 40, NULL, '2026-04-12 18:51:52', NULL, '2026-07-22 22:02:34'),
('4', 'TOKEN_REFRESH_EXPIRE', '604800', 'NUMBER', 'SECURITY', 'Refresh Token 过期时间（秒），默认 7 天', 1, 1, 50, NULL, '2026-04-12 18:51:52', NULL, '2026-07-22 22:02:34'),
('5', 'LOGIN_MAX_RETRY', '5', 'NUMBER', 'SECURITY', '登录最大重试次数，超过后锁定账号', 1, 1, 20, NULL, '2026-04-12 18:51:52', NULL, '2026-07-22 22:02:34'),
('6', 'LOGIN_LOCK_DURATION', '1800', 'NUMBER', 'SECURITY', '账号锁定时长（秒），默认 30 分钟', 1, 1, 30, NULL, '2026-04-12 18:51:52', NULL, '2026-07-22 22:02:34'),
('7', 'SITE_TITLE', 'Yu Flow 低代码平台', 'STRING', 'GENERAL', '系统名称，显示在页面标题和登录页', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-04-12 18:51:52'),
('8', 'FILE_UPLOAD_MAX_SIZE', '10485760', 'NUMBER', 'GENERAL', '文件上传最大大小（字节），默认 10MB', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-04-12 18:51:52'),
('9', 'PAGINATION_DEFAULT_SIZE', '10', 'NUMBER', 'GENERAL', '默认分页条数', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-04-12 18:51:52'),
('10', 'ASSET_VERSION_RETENTION_COUNT', '20', 'NUMBER', 'FLOW', '接口/任务/服务编排历史版本保留条数（每个资产最多保留最近 N 条，超出自动删除最旧版本；最小为 1）', 1, 1, 100, NULL, '2026-07-20 21:35:31', NULL, '2026-07-20 21:35:31'),
('11', 'OPEN_ENABLED', 'true', 'BOOLEAN', 'OPEN', '开放入口总开关（/flow-api/open/**）。停用本项后回退 yu.flow.open.enabled', 1, 1, 10, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('12', 'OPEN_ALLOW_PLAIN_SECRET', 'false', 'BOOLEAN', 'OPEN', '是否允许 X-Yu-App-Secret 明文头（生产务必 false）', 1, 1, 20, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('13', 'OPEN_ALLOW_DIRECT_PATH', 'false', 'BOOLEAN', 'OPEN', '是否允许 AppKey 直打真实发布 path（默认仅开放前缀入口）', 1, 1, 30, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('14', 'OPEN_REQUIRE_HOST_AUTH', 'true', 'BOOLEAN', 'OPEN', '无 AppKey 访问已发布 API 时是否强制宿主登录（JWT）', 1, 1, 40, NULL, '2026-07-22 20:01:00', NULL, '2026-07-23 17:57:23'),
('15', 'OPEN_CALL_LOG_ENABLED', 'true', 'BOOLEAN', 'OPEN', '开放入站摘要日志全局开关（平台可单独关闭）', 1, 1, 50, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('16', 'OPEN_SKEW_SECONDS', '300', 'NUMBER', 'OPEN', 'HMAC 签名时钟偏差（秒）', 1, 1, 60, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('17', 'OPEN_NONCE_FAIL_CLOSED', 'true', 'BOOLEAN', 'OPEN', 'nonce 写入 Redis 失败时是否拒绝请求（生产建议 true）', 1, 1, 70, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('18', 'OPEN_INCLUDE_BODY_HASH', 'true', 'BOOLEAN', 'OPEN', 'HMAC 是否纳入 body SHA-256', 1, 1, 80, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('19', 'OPEN_ROTATE_GRACE_HOURS', '24', 'NUMBER', 'OPEN', '密钥轮换后旧密可用宽限期（小时）；≤0 立即失效', 1, 1, 90, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('20', 'INGRESS_ENABLED', 'true', 'BOOLEAN', 'INGRESS', '已发布 API 入站防护总开关（安全默认开启；false=关闭后网关仍强制管理端 JWT）', 1, 1, 10, NULL, '2026-07-22 20:01:00', NULL, '2026-07-23 17:57:23'),
('21', 'INGRESS_DEFAULT_AUTH_MODE', 'NONE', 'ENUM', 'INGRESS', '[HOST:需管理端登录|OPEN:开放平台鉴权|NONE:无鉴权（仍受 allow-ingress-auth-none 约束）] 默认鉴权：NONE | HOST | OPEN（接口可覆盖）', 1, 1, 20, NULL, '2026-07-22 20:01:00', NULL, '2026-07-24 23:33:30'),
('22', 'INGRESS_DEFAULT_ANTI_REPLAY', 'true', 'BOOLEAN', 'INGRESS', '默认防重放（仅 OPEN 鉴权生效）', 1, 1, 30, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('23', 'INGRESS_DEFAULT_RATE_LIMIT_ENABLED', 'false', 'BOOLEAN', 'INGRESS', '默认是否启用接口限流', 1, 1, 40, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('24', 'INGRESS_DEFAULT_RATE_LIMIT_QPS', '100', 'NUMBER', 'INGRESS', '默认限流 QPS（秒级固定窗口）', 1, 1, 50, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('25', 'INGRESS_DEFAULT_IP_ALLOWLIST', '', 'STRING', 'INGRESS', '默认 IP 白名单（空=不限制；逗号分隔 IP/CIDR）', 1, 1, 60, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('26', 'INGRESS_RATE_LIMIT_FAIL_OPEN', 'true', 'BOOLEAN', 'INGRESS', '限流 Redis 失败时是否放行（fail-open）', 1, 1, 70, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('27', 'RBAC_ENABLED', 'true', 'BOOLEAN', 'SECURITY', '是否启用数据库用户 RBAC；false 时登录回退 yu.flow.username/password', 1, 1, 10, NULL, '2026-07-22 20:52:46', NULL, '2026-07-22 22:02:34'),
('28', 'INGRESS_DEFAULT_TIMEOUT_MS', '30000', 'NUMBER', 'INGRESS', '已发布 API 默认执行超时（毫秒）。≤0 不限制。接口 securityConfig.timeoutMs 可覆盖，改完需发布', 1, 1, 80, NULL, '2026-07-22 21:54:31', NULL, '2026-07-22 21:59:12'),
('29', 'ALERT_ENABLED', 'false', 'BOOLEAN', 'ALERT', '运行告警总开关。开启后按间隔扫描近窗口异常并推送 Webhook', 1, 1, 10, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('30', 'ALERT_WEBHOOK_URL', '', 'STRING', 'ALERT', 'Webhook URL（钉钉/企微自定义机器人或任意 HTTP 接收端）', 1, 1, 20, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('31', 'ALERT_INTERVAL_MINUTES', '15', 'NUMBER', 'ALERT', '扫描间隔（分钟）', 1, 1, 30, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('32', 'ALERT_TOP_N', '10', 'NUMBER', 'ALERT', '每次最多推送异常条数', 1, 1, 40, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('33', 'ALERT_WINDOW', '24h', 'STRING', 'ALERT', '指标窗口：1h / 24h / 7d', 1, 1, 50, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('34', 'ALERT_MIN_HEALTH', 'error', 'STRING', 'ALERT', '最低告警健康度：error 仅严重；warn 含预警', 1, 1, 60, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('35', 'ALERT_DEDUP_MINUTES', '60', 'NUMBER', 'ALERT', '同一资产告警去重静默（分钟）', 1, 1, 70, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('36', 'MAIL_ENABLED', 'false', 'BOOLEAN', 'MAIL', '邮件发送总开关。关闭后告警 Email 通道与后续邮件节点均不可发信', 1, 1, 10, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('37', 'MAIL_HOST', '', 'STRING', 'MAIL', 'SMTP 主机，如 smtp.qq.com / smtp.163.com', 1, 1, 20, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('38', 'MAIL_PORT', '465', 'NUMBER', 'MAIL', 'SMTP 端口：SSL 常用 465，STARTTLS 常用 587', 1, 1, 30, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('39', 'MAIL_USERNAME', '', 'STRING', 'MAIL', 'SMTP 登录账号（通常为邮箱地址）', 1, 1, 40, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('40', 'MAIL_PASSWORD', '', 'STRING', 'MAIL', 'SMTP 密码或应用专用密码', 1, 1, 50, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('41', 'MAIL_FROM', '', 'STRING', 'MAIL', '发件人地址；为空则使用 MAIL_USERNAME', 1, 1, 60, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('42', 'MAIL_SSL', 'true', 'BOOLEAN', 'MAIL', '启用 SMTPS/SSL（465）', 1, 1, 70, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('43', 'MAIL_STARTTLS', 'false', 'BOOLEAN', 'MAIL', '启用 STARTTLS（587）；与 SSL 二选一为主', 1, 1, 80, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('44', 'LOG_EXECUTION_RETENTION_DAYS', '30', 'NUMBER', 'LOG', 'API 执行日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 10, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('45', 'LOG_LOGIN_RETENTION_DAYS', '90', 'NUMBER', 'LOG', '登录审计日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 20, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('46', 'LOG_TASK_RETENTION_DAYS', '30', 'NUMBER', 'LOG', '定时任务执行日志保留天数（flow_log_task；0 = 不清理）', 1, 1, 30, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('47', 'LOG_SERVICE_RETENTION_DAYS', '30', 'NUMBER', 'LOG', '服务编排执行日志保留天数（flow_log_service；0 = 不清理）', 1, 1, 40, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('48', 'LOG_THIRD_RETENTION_DAYS', '30', 'NUMBER', 'LOG', '第三方调用日志保留天数（flow_log_third；0 = 不清理）', 1, 1, 50, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('49', 'LOG_OPEN_CALL_RETENTION_DAYS', '30', 'NUMBER', 'LOG', '开放平台调用日志保留天数（flow_log_open_call；0 = 不清理）', 1, 1, 60, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('50', 'LOG_AUDIT_RETENTION_DAYS', '180', 'NUMBER', 'LOG', '配置变更审计日志保留天数（flow_log_audit；0 = 不清理）', 1, 1, 70, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('51', 'LOG_ALERT_EVENT_RETENTION_DAYS', '90', 'NUMBER', 'LOG', '告警历史事件保留天数（flow_log_alert；0 = 不清理）', 1, 1, 80, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('52', 'SCRIPT_ALLOWED_LANGUAGES', 'aviator,spel,javascript,groovy', 'STRING', 'SECURITY', 'Evaluate/Switch 等节点允许的脚本语言白名单（逗号分隔）。空=不限制；未在白名单的语言将不可用。', 1, 1, 100, NULL, '2026-07-27 20:25:31', NULL, '2026-07-27 20:25:31'),
('53', 'ENGINE_DEFAULT_LOG_MODE', 'ERROR_ONLY', 'ENUM', 'LOG', '[ERROR_ONLY:仅错误|ALL:全量记录|OFF:完全关闭] 全局默认日志策略。当接口、任务、消息队列或服务编排设置为「继承全局」时，默认生效的日志落库策略。', 1, 1, 15, NULL, '2026-08-01 19:30:35', NULL, '2026-08-01 19:35:51'),
('54', 'MQ_LOG_PAYLOAD_MODE', 'FULL', 'ENUM', 'LOG', '[FULL:明文|MASK:脱敏占位|OFF:不存报文] MQ 消费日志原始报文全局默认策略。任务设为「继承全局」时生效。', 1, 1, 16, NULL, '2026-08-03 19:35:29', NULL, '2026-08-03 19:35:29'),
('55', 'PRIVACY_WRAP_TRANSPORT', 'true', 'BOOLEAN', 'PRIVACY', '已发布 JSON 明文档是否套传输 SM4（X-Privacy-Key）。true=套信封，无会话密钥则 REVEAL 降级脱敏；false=命中明文规则时直接返回明文（内网/调试，生产不建议关）。本项优先于 yml，修改后热更新', 1, 1, 10, NULL, '2026-08-20 17:00:00', NULL, '2026-08-20 17:00:00')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_sys_macro (8) =====

INSERT INTO `flow_sys_macro` (`id`, `macro_code`, `macro_name`, `macro_type`, `expression`, `scope`, `return_type`, `status`, `remark`, `macro_params`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('1', 'UUID', '32位UUID（无连字符）', 'VARIABLE', 'T(cn.hutool.core.util.IdUtil).fastSimpleUUID()', 'ALL', 'String', 1, '生成32位无连字符的UUID字符串', NULL, NULL, '2026-03-24 08:25:06', 'SYSTEM_INIT', '2026-03-28 00:00:35'),
('2', 'DATE_FORMAT', '日期格式化', 'FUNCTION', 'T(java.time.format.DateTimeFormatter).ofPattern(#format).format(#date)', 'ALL', 'String', 1, NULL, 'format, date', NULL, '2026-03-25 00:49:22', NULL, '2026-04-02 00:28:54'),
('3', 'DATE_TIME_FULL', '完整日期时间（含毫秒）', 'VARIABLE', 'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd HH:mm:ss.SSS''))', 'ALL', 'String', 1, '格式：yyyy-MM-dd HH:mm:ss.SSS', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-03-27 00:45:34'),
('4', 'DATE_TIME', '标准日期时间', 'VARIABLE', 'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd HH:mm:ss''))', 'ALL', 'String', 1, '格式：yyyy-MM-dd HH:mm:ss', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-03-27 00:45:34'),
('5', 'DATE', '当前日期', 'VARIABLE', 'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd''))', 'ALL', 'String', 1, '格式：yyyy-MM-dd', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-03-27 00:45:34'),
('6', 'TIME', '当前时间', 'VARIABLE', 'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''HH:mm:ss''))', 'ALL', 'String', 1, '格式：HH:mm:ss', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-03-27 00:45:34'),
('7', 'SNOWFLAKE', '雪花算法ID', 'VARIABLE', 'T(org.yu.flow.auto.util.SnowIdGenerator).getId()', 'ALL', 'String', 1, '基于雪花算法生成分布式唯一ID字符串', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-04-02 00:12:05'),
('8', 'GET_ENV', '获取环境配置', 'FUNCTION', '@environment.getProperty(#p0)', 'ALL', 'String', 1, '动态获取 Spring Environment 配置项，如 spring.datasource.url。调用方式：宏编码 GET_ENV，上下文参数 {p0: "配置key"}', 'p0', 'SYSTEM_INIT', '2026-03-27 00:45:34', NULL, '2026-03-27 00:45:34')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_env (3) =====

INSERT INTO `flow_env` (`id`, `code`, `name`, `require_suite_pass`, `pass_ttl_hours`, `enabled`, `sort_order`, `remark`, `create_time`, `update_time`) VALUES
('env_dev', 'DEV', '开发', 0, 72, 1, 10, '默认发布环境，不强制回归', '2026-07-24 11:28:05', '2026-07-24 11:28:05'),
('env_staging', 'STAGING', '预发', 1, 48, 1, 20, '发布前需回归通过', '2026-07-24 11:28:05', '2026-07-24 11:28:05'),
('env_prod', 'PROD', '生产', 1, 24, 1, 30, '发布前需回归通过（24h 内）', '2026-07-24 11:28:05', '2026-07-24 11:28:05')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_response_template (1) =====
-- 全局默认响应包装；缺省时网关无法套壳

INSERT INTO `flow_response_template` (`id`, `template_name`, `success_wrapper`, `page_wrapper`, `fail_wrapper`, `is_default`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('tpl_default_standard', '标准响应模板',
 '{"code": 200, "message": "success", "data": "$"}',
 '{"code": 200, "message": "success", "data": {"items": "$.items", "page": "$.page", "total": "$.total", "current": "$.current", "size": "$.size", "pages": "$.pages"}}',
 '{"code": 500, "message": "$.msg", "data": null}',
 1, '系统内置标准响应格式，适用于大多数业务场景', 'SYSTEM_INIT', '2026-08-12 16:00:00', 'SYSTEM_INIT', '2026-08-12 16:00:00')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_db_connection (1) =====

INSERT INTO `flow_db_connection` (`id`, `code`, `name`, `db_type`, `driver_class_name`, `url`, `username`, `password`, `initial_size`, `min_idle`, `max_active`, `status`, `wall_config`, `is_system`, `health_status`, `error_count`, `last_error_msg`, `create_time`, `update_time`) VALUES
('2080671779158224896', '[DEFAULT]', '系统默认数据源', 'mysql', 'com.mysql.cj.jdbc.Driver', 'jdbc:mysql://127.0.0.1:3306/flow?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true', 'root', '', 5, 5, 20, 1, '{"enabled":true,"multiStatementAllow":false,"commentAllow":false,"noneBaseStatementAllow":false,"selectAllow":true,"insertAllow":true,"updateAllow":true,"deleteAllow":true,"tableCheck":true,"tableWhiteList":[],"tableBlackList":[],"tableReadOnlyList":[],"functionBlackList":["sleep","benchmark","load_file","updatexml","extractvalue","pg_sleep"],"variantCheck":true}', 1, 'UNKNOWN', 0, NULL, '2026-07-24 23:09:43', '2026-07-24 23:09:43')
ON DUPLICATE KEY UPDATE id = VALUES(id);
