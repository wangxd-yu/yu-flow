-- 缩略图：场景开关 + 台账缩略图字段
ALTER TABLE flow_oss_upload_profile
  ADD COLUMN IF NOT EXISTS thumbnail_enabled smallint NOT NULL DEFAULT 0;

COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_enabled IS '是否异步生成缩略图：0=否, 1=是';

ALTER TABLE flow_oss_object
  ADD COLUMN IF NOT EXISTS thumb_status varchar(16) NOT NULL DEFAULT 'NONE',
  ADD COLUMN IF NOT EXISTS thumb_object_key varchar(1024),
  ADD COLUMN IF NOT EXISTS thumb_public_path varchar(1024),
  ADD COLUMN IF NOT EXISTS thumb_content_type varchar(128),
  ADD COLUMN IF NOT EXISTS thumb_size_bytes bigint,
  ADD COLUMN IF NOT EXISTS thumb_error varchar(512);

COMMENT ON COLUMN flow_oss_object.thumb_status IS 'NONE / PENDING / READY / FAILED / SKIPPED';
COMMENT ON COLUMN flow_oss_object.thumb_object_key IS '缩略图对象键';
COMMENT ON COLUMN flow_oss_object.thumb_public_path IS '缩略图公有路径';
COMMENT ON COLUMN flow_oss_object.thumb_content_type IS '缩略图 Content-Type';
COMMENT ON COLUMN flow_oss_object.thumb_size_bytes IS '缩略图大小';
COMMENT ON COLUMN flow_oss_object.thumb_error IS '缩略图失败原因';

CREATE INDEX IF NOT EXISTS idx_flow_oss_object_thumb_status ON flow_oss_object (thumb_status);
