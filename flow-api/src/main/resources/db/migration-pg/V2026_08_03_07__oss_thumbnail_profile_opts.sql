-- 缩略图：场景级自定义参数（空=回退全局）
ALTER TABLE flow_oss_upload_profile
  ADD COLUMN IF NOT EXISTS thumbnail_max_edge integer,
  ADD COLUMN IF NOT EXISTS thumbnail_max_source_bytes bigint,
  ADD COLUMN IF NOT EXISTS thumbnail_jpeg_quality numeric(3,2);

COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_max_edge IS '缩略图最长边像素，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_max_source_bytes IS '参与缩略图的源文件上限，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.thumbnail_jpeg_quality IS 'JPEG 质量 0~1，空=用全局';
