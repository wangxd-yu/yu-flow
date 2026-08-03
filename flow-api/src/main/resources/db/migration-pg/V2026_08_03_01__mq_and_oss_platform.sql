-- 2026-08-03 合并增量：MQ 可观测增强 + MinIO 对象存储平台（M1–M3 + 缩略图）
-- 由 V2026_08_03_01～07 合并；新环境一次执行即可。

-- =============================================================================
-- MQ：message_id 索引
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_message_id ON flow_mq_task_log (message_id);

-- =============================================================================
-- MQ：敏感报文策略 / 重试 / 死信 + 全局 MQ_LOG_PAYLOAD_MODE 种子
-- =============================================================================
ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS log_payload_mode VARCHAR(16) DEFAULT 'SYSTEM_DEFAULT';
COMMENT ON COLUMN flow_mq_task_info.log_payload_mode IS '原始报文落库策略：SYSTEM_DEFAULT/FULL/MASK/OFF';

ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS retry_max INTEGER DEFAULT 0;
COMMENT ON COLUMN flow_mq_task_info.retry_max IS '失败重试次数（0=不重试）';

ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS retry_backoff_ms INTEGER DEFAULT 1000;
COMMENT ON COLUMN flow_mq_task_info.retry_backoff_ms IS '重试间隔毫秒';

ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS dead_letter_topic VARCHAR(255) DEFAULT NULL;
COMMENT ON COLUMN flow_mq_task_info.dead_letter_topic IS '最终失败时转发的死信 topic/队列';

INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, sort_order, create_time, update_time)
VALUES ('MQ_LOG_PAYLOAD_MODE', 'FULL', 'ENUM', 'LOG', '[FULL:明文|MASK:脱敏占位|OFF:不存报文] MQ 消费日志原始报文全局默认策略。任务设为「继承全局」时生效。', 1, 1, 16, NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;

-- =============================================================================
-- OSS：连接 / 上传场景 / 台账 / 下载审计 / 业务引用（终态）
-- =============================================================================
CREATE TABLE IF NOT EXISTS flow_oss_connection (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  code varchar(64) NOT NULL,
  endpoint varchar(512) NOT NULL,
  access_key varchar(128),
  secret_key varchar(512),
  region varchar(64),
  path_style smallint NOT NULL DEFAULT 1,
  public_bucket varchar(128),
  private_bucket varchar(128),
  public_base_url varchar(512),
  key_prefix varchar(256),
  public_access_mode varchar(32) DEFAULT 'NGINX_PROXY',
  private_download_mode varchar(16) NOT NULL DEFAULT 'STREAM',
  presign_expire_seconds integer NOT NULL DEFAULT 300,
  enabled smallint NOT NULL DEFAULT 1,
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
COMMENT ON COLUMN flow_oss_connection.endpoint IS 'MinIO endpoint';
COMMENT ON COLUMN flow_oss_connection.secret_key IS 'Secret Key（AES 密文）';
COMMENT ON COLUMN flow_oss_connection.path_style IS '是否 path-style 访问';
COMMENT ON COLUMN flow_oss_connection.public_access_mode IS 'ANON / NGINX_PROXY';
COMMENT ON COLUMN flow_oss_connection.private_download_mode IS '隐私下载：STREAM / PRESIGN';
COMMENT ON COLUMN flow_oss_connection.presign_expire_seconds IS '预签名有效期（秒）';
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_code ON flow_oss_connection (code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_enabled ON flow_oss_connection (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_create_time ON flow_oss_connection (create_time);

CREATE TABLE IF NOT EXISTS flow_oss_upload_profile (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  code varchar(64) NOT NULL,
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
  thumbnail_enabled smallint NOT NULL DEFAULT 0,
  thumbnail_max_edge integer,
  thumbnail_max_source_bytes bigint,
  thumbnail_jpeg_quality numeric(3,2),
  require_auth smallint NOT NULL DEFAULT 1,
  biz_fields_schema text,
  access_perm varchar(128),
  enabled smallint NOT NULL DEFAULT 1,
  remark varchar(512),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_upload_profile IS 'OSS 上传场景';
COMMENT ON COLUMN flow_oss_upload_profile.visibility IS 'PUBLIC / PRIVATE';
COMMENT ON COLUMN flow_oss_upload_profile.quota_max_bytes IS '场景容量配额（字节），空=不限';
COMMENT ON COLUMN flow_oss_upload_profile.quota_max_files IS '场景文件数配额，空=不限';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_enabled IS '是否异步生成缩略图：0=否, 1=是';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_max_edge IS '缩略图最长边像素，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_max_source_bytes IS '参与缩略图的源文件上限，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_jpeg_quality IS 'JPEG 质量 0~1，空=用全局';
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_code ON flow_oss_upload_profile (code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_connection_code ON flow_oss_upload_profile (connection_code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_enabled ON flow_oss_upload_profile (enabled);

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
  uploaded_by_name varchar(128),
  dept_id varchar(64),
  status varchar(16) NOT NULL DEFAULT 'ACTIVE',
  expires_at timestamp,
  object_purged smallint NOT NULL DEFAULT 0,
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
COMMENT ON COLUMN flow_oss_object.status IS 'ACTIVE / DELETED';
COMMENT ON COLUMN flow_oss_object.expires_at IS '临时文件过期时间，空=不过期';
COMMENT ON COLUMN flow_oss_object.object_purged IS 'MinIO 对象是否已物理删除：0=否, 1=是';
COMMENT ON COLUMN flow_oss_object.thumb_status IS 'NONE / PENDING / READY / FAILED / SKIPPED';
COMMENT ON COLUMN flow_oss_object.thumb_object_key IS '缩略图对象键';
COMMENT ON COLUMN flow_oss_object.thumb_public_path IS '缩略图公有路径';
COMMENT ON COLUMN flow_oss_object.thumb_content_type IS '缩略图 Content-Type';
COMMENT ON COLUMN flow_oss_object.thumb_size_bytes IS '缩略图大小';
COMMENT ON COLUMN flow_oss_object.thumb_error IS '缩略图失败原因';
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_profile_code ON flow_oss_object (profile_code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_uploaded_by ON flow_oss_object (uploaded_by);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_dept_id ON flow_oss_object (dept_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_status ON flow_oss_object (status);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_create_time ON flow_oss_object (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_expires_at ON flow_oss_object (expires_at);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_purge ON flow_oss_object (status, object_purged);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_thumb_status ON flow_oss_object (thumb_status);

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
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_object_id ON flow_oss_download_log (object_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_create_time ON flow_oss_download_log (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_result ON flow_oss_download_log (result);

CREATE TABLE IF NOT EXISTS flow_oss_object_ref (
  id varchar(32) NOT NULL,
  object_id varchar(32) NOT NULL,
  biz_type varchar(64) NOT NULL,
  biz_id varchar(128) NOT NULL,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_object_ref IS '对象存储业务引用';
COMMENT ON COLUMN flow_oss_object_ref.id IS '雪花ID';
COMMENT ON COLUMN flow_oss_object_ref.object_id IS '台账ID';
COMMENT ON COLUMN flow_oss_object_ref.biz_type IS '业务类型';
COMMENT ON COLUMN flow_oss_object_ref.biz_id IS '业务单据ID';
COMMENT ON COLUMN flow_oss_object_ref.create_time IS '创建时间';
CREATE UNIQUE INDEX IF NOT EXISTS uk_flow_oss_object_ref_biz ON flow_oss_object_ref (object_id, biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_ref_object_id ON flow_oss_object_ref (object_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_ref_biz ON flow_oss_object_ref (biz_type, biz_id);

-- OSS 权限种子（与 MySQL 同 id）
INSERT INTO flow_sys_permission (id, perm_code, perm_name, group_code, remark, create_time)
VALUES
('p_oss_v', 'flow:oss:view', '对象存储查看', 'flow', 'OSS 连接/场景/台账查看', CURRENT_TIMESTAMP),
('p_oss_w', 'flow:oss:write', '对象存储编排', 'flow', 'OSS 连接/场景编辑', CURRENT_TIMESTAMP),
('p_oss_a', 'flow:oss:admin', '对象存储管理', 'flow', '跨用户台账与隐私下载（内置 Scope=ALL）', CURRENT_TIMESTAMP),
('p_oss_t', 'flow:oss:audit', '对象存储审计', 'flow', '隐私下载审计日志', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET perm_name = EXCLUDED.perm_name;

INSERT INTO flow_sys_role_permission (role_id, perm_code)
VALUES
('role_operator', 'flow:oss:view'),
('role_operator', 'flow:oss:write'),
('role_viewer', 'flow:oss:view')
ON CONFLICT (role_id, perm_code) DO NOTHING;
