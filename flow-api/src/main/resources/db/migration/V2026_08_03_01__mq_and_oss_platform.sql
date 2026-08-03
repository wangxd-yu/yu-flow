-- 2026-08-03 合并增量：MQ 可观测增强 + MinIO 对象存储平台（M1–M3 + 缩略图）
-- 由 V2026_08_03_01～07 合并；新环境一次执行即可。

-- =============================================================================
-- MQ：message_id 索引
-- =============================================================================
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_log'
    AND INDEX_NAME = 'idx_flow_mq_task_log_message_id'
);
SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE `flow_mq_task_log` ADD INDEX `idx_flow_mq_task_log_message_id` (`message_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================================
-- MQ：敏感报文策略 / 重试 / 死信 + 全局 MQ_LOG_PAYLOAD_MODE 种子
-- =============================================================================
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_info'
    AND COLUMN_NAME = 'log_payload_mode'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_mq_task_info` ADD COLUMN `log_payload_mode` VARCHAR(16) DEFAULT ''SYSTEM_DEFAULT'' COMMENT ''原始报文落库策略：SYSTEM_DEFAULT/FULL/MASK/OFF''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_info'
    AND COLUMN_NAME = 'retry_max'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_mq_task_info` ADD COLUMN `retry_max` INT DEFAULT 0 COMMENT ''失败重试次数（0=不重试）''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_info'
    AND COLUMN_NAME = 'retry_backoff_ms'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_mq_task_info` ADD COLUMN `retry_backoff_ms` INT DEFAULT 1000 COMMENT ''重试间隔毫秒''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_info'
    AND COLUMN_NAME = 'dead_letter_topic'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_mq_task_info` ADD COLUMN `dead_letter_topic` VARCHAR(255) DEFAULT NULL COMMENT ''最终失败时转发的死信 topic/队列''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, sort_order, create_time, update_time)
VALUES ('MQ_LOG_PAYLOAD_MODE', 'FULL', 'ENUM', 'LOG', '[FULL:明文|MASK:脱敏占位|OFF:不存报文] MQ 消费日志原始报文全局默认策略。任务设为「继承全局」时生效。', 1, 1, 16, NOW(), NOW())
ON DUPLICATE KEY UPDATE config_value = config_value;

-- =============================================================================
-- OSS：连接 / 上传场景 / 台账 / 下载审计 / 业务引用（终态）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `flow_oss_connection` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '连接名称',
  `code` varchar(64) NOT NULL COMMENT '连接编码（未删除记录内唯一）',
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

CREATE TABLE IF NOT EXISTS `flow_oss_upload_profile` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `name` varchar(128) NOT NULL COMMENT '场景名称',
  `code` varchar(64) NOT NULL COMMENT '场景编码（未删除记录内唯一）',
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
  `biz_fields_schema` text COMMENT '业务字段 JSON Schema',
  `access_perm` varchar(128) COMMENT '上传所需权限码（可空）',
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
  `uploaded_by` varchar(64) COMMENT '上传人 userId',
  `uploaded_by_name` varchar(128) COMMENT '上传人展示名',
  `dept_id` varchar(64) COMMENT '部门 ID（数据权限）',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED',
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
  KEY `idx_flow_oss_object_uploaded_by` (`uploaded_by`),
  KEY `idx_flow_oss_object_dept_id` (`dept_id`),
  KEY `idx_flow_oss_object_status` (`status`),
  KEY `idx_flow_oss_object_create_time` (`create_time`),
  KEY `idx_flow_oss_object_expires_at` (`expires_at`),
  KEY `idx_flow_oss_object_purge` (`status`, `object_purged`),
  KEY `idx_flow_oss_object_thumb_status` (`thumb_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 文件台账';

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

CREATE TABLE IF NOT EXISTS `flow_oss_object_ref` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `object_id` varchar(32) NOT NULL COMMENT '台账ID',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_id` varchar(128) NOT NULL COMMENT '业务单据ID',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_oss_object_ref_biz` (`object_id`, `biz_type`, `biz_id`),
  KEY `idx_flow_oss_object_ref_object_id` (`object_id`),
  KEY `idx_flow_oss_object_ref_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储业务引用';

-- OSS 权限种子
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
('p_oss_v', 'flow:oss:view', '对象存储查看', 'flow', 'OSS 连接/场景/台账查看', NOW()),
('p_oss_w', 'flow:oss:write', '对象存储编排', 'flow', 'OSS 连接/场景编辑', NOW()),
('p_oss_a', 'flow:oss:admin', '对象存储管理', 'flow', '跨用户台账与隐私下载（内置 Scope=ALL）', NOW()),
('p_oss_t', 'flow:oss:audit', '对象存储审计', 'flow', '隐私下载审计日志', NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
('role_operator', 'flow:oss:view'),
('role_operator', 'flow:oss:write'),
('role_viewer', 'flow:oss:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);
