-- 缩略图：场景级自定义参数（空=回退全局）
ALTER TABLE `flow_oss_upload_profile`
  ADD COLUMN `thumbnail_max_edge` int DEFAULT NULL COMMENT '缩略图最长边像素，空=用全局' AFTER `thumbnail_enabled`,
  ADD COLUMN `thumbnail_max_source_bytes` bigint DEFAULT NULL COMMENT '参与缩略图的源文件上限，空=用全局' AFTER `thumbnail_max_edge`,
  ADD COLUMN `thumbnail_jpeg_quality` decimal(3,2) DEFAULT NULL COMMENT 'JPEG 质量 0~1，空=用全局' AFTER `thumbnail_max_source_bytes`;
