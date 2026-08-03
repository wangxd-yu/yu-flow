-- Table: flow_oss_object
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
COMMENT ON COLUMN flow_oss_object.object_purged IS 'MinIO 对象是否已物理删除';
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
