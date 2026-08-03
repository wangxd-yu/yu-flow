-- MinIO 对象存储平台 M1：四张表 + flow:oss 权限种子（PostgreSQL）

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
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_object IS 'OSS 文件台账';
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_profile_code ON flow_oss_object (profile_code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_uploaded_by ON flow_oss_object (uploaded_by);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_dept_id ON flow_oss_object (dept_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_status ON flow_oss_object (status);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_create_time ON flow_oss_object (create_time);

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

INSERT INTO flow_sys_permission (id, perm_code, perm_name, group_code, remark, create_time)
VALUES
('p_oss_v', 'flow:oss:view', 'OSS查看', 'flow', 'OSS 连接与上传场景查看', CURRENT_TIMESTAMP),
('p_oss_w', 'flow:oss:write', 'OSS编排', 'flow', 'OSS 连接与上传场景编辑', CURRENT_TIMESTAMP),
('p_oss_a', 'flow:oss:admin', 'OSS管理', 'flow', 'OSS 文件管理员（内置 Scope ALL）', CURRENT_TIMESTAMP),
('p_oss_audit', 'flow:oss:audit', 'OSS审计', 'flow', 'OSS 隐私下载审计查看', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET perm_name = EXCLUDED.perm_name;

INSERT INTO flow_sys_role_permission (role_id, perm_code)
VALUES
('role_operator', 'flow:oss:view'),
('role_operator', 'flow:oss:write'),
('role_viewer', 'flow:oss:view')
ON CONFLICT (role_id, perm_code) DO NOTHING;
