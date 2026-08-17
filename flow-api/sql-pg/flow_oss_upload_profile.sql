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
