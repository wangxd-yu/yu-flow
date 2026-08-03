-- Table: flow_oss_connection
-- MinIO / S3 兼容对象存储连接配置
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

-- Table: flow_oss_upload_profile
-- OSS 上传场景（业务 Profile）
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
  `uploaded_by` varchar(64) COMMENT '上传人 userId',
  `uploaded_by_name` varchar(128) COMMENT '上传人展示名',
  `dept_id` varchar(64) COMMENT '部门 ID（数据权限）',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_oss_object_profile_code` (`profile_code`),
  KEY `idx_flow_oss_object_uploaded_by` (`uploaded_by`),
  KEY `idx_flow_oss_object_dept_id` (`dept_id`),
  KEY `idx_flow_oss_object_status` (`status`),
  KEY `idx_flow_oss_object_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 文件台账';

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


-- 权限种子
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
