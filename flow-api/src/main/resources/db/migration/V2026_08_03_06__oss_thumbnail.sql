-- 缩略图：场景开关 + 台账缩略图字段
ALTER TABLE `flow_oss_upload_profile`
  ADD COLUMN `thumbnail_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否异步生成缩略图：0=否, 1=是' AFTER `quota_max_files`;

ALTER TABLE `flow_oss_object`
  ADD COLUMN `thumb_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE / PENDING / READY / FAILED / SKIPPED' AFTER `object_purged`,
  ADD COLUMN `thumb_object_key` varchar(1024) DEFAULT NULL COMMENT '缩略图对象键' AFTER `thumb_status`,
  ADD COLUMN `thumb_public_path` varchar(1024) DEFAULT NULL COMMENT '缩略图公有路径' AFTER `thumb_object_key`,
  ADD COLUMN `thumb_content_type` varchar(128) DEFAULT NULL COMMENT '缩略图 Content-Type' AFTER `thumb_public_path`,
  ADD COLUMN `thumb_size_bytes` bigint DEFAULT NULL COMMENT '缩略图大小' AFTER `thumb_content_type`,
  ADD COLUMN `thumb_error` varchar(512) DEFAULT NULL COMMENT '缩略图失败原因' AFTER `thumb_size_bytes`;

CREATE INDEX `idx_flow_oss_object_thumb_status` ON `flow_oss_object` (`thumb_status`);
