-- Table: flow_oss_upload_profile
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
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_enabled IS '是否异步生成缩略图：0=否, 1=是';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_max_edge IS '缩略图最长边像素，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_max_source_bytes IS '参与缩略图的源文件上限，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_jpeg_quality IS 'JPEG 质量 0~1，空=用全局';
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_code ON flow_oss_upload_profile (code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_connection_code ON flow_oss_upload_profile (connection_code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_upload_profile_enabled ON flow_oss_upload_profile (enabled);
