-- 2026-08-19：OSS zip 异步展开（场景开关 + 台账父子关系）
-- 与 db/migration-pg/V2026_08_19_01__oss_archive_extract.sql 语义等价

-- ===== flow_oss_upload_profile =====
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'extract_archive_enabled');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `extract_archive_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''上传 zip 后是否异步展开：0=否, 1=是'' AFTER `thumbnail_jpeg_quality`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'extract_keep_archive');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `extract_keep_archive` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''展开成功后是否保留原包：1=保留, 0=软删原包'' AFTER `extract_archive_enabled`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'extract_reject_policy');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `extract_reject_policy` varchar(32) NOT NULL DEFAULT ''SKIP_ZERO_FAIL'' COMMENT ''不合格条目：SKIP_ZERO_FAIL / FAIL_PACK'' AFTER `extract_keep_archive`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'extract_allowed_extensions');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `extract_allowed_extensions` varchar(512) COMMENT ''展开后落库扩展名白名单，空=不限制（仍排除嵌套压缩包）'' AFTER `extract_reject_policy`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'extract_allowed_content_types');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `extract_allowed_content_types` text COMMENT ''展开后落库 MIME 白名单，空=不限制'' AFTER `extract_allowed_extensions`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'extract_max_entries');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `extract_max_entries` int DEFAULT NULL COMMENT ''单包最多处理条目数，空=用全局'' AFTER `extract_allowed_content_types`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'extract_max_uncompressed_bytes');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `extract_max_uncompressed_bytes` bigint DEFAULT NULL COMMENT ''单包解压后总字节上限，空=用全局'' AFTER `extract_max_entries`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== flow_oss_object =====
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_object'
    AND COLUMN_NAME = 'parent_object_id');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_object` ADD COLUMN `parent_object_id` varchar(32) DEFAULT NULL COMMENT ''来源压缩包台账 ID，空=独立上传'' AFTER `thumb_error`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_object'
    AND COLUMN_NAME = 'archive_entry_path');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_object` ADD COLUMN `archive_entry_path` varchar(512) DEFAULT NULL COMMENT ''包内相对路径（正斜杠）'' AFTER `parent_object_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_object'
    AND COLUMN_NAME = 'extract_status');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_object` ADD COLUMN `extract_status` varchar(16) NOT NULL DEFAULT ''NONE'' COMMENT ''NONE / PENDING / EXTRACTING / DONE / FAILED / SKIPPED'' AFTER `archive_entry_path`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_object'
    AND COLUMN_NAME = 'extract_error');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_oss_object` ADD COLUMN `extract_error` varchar(512) DEFAULT NULL COMMENT ''展开结果摘要或失败原因'' AFTER `extract_status`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_object'
    AND INDEX_NAME = 'idx_flow_oss_object_parent_object_id');
SET @ddl := IF(@idx = 0,
  'ALTER TABLE `flow_oss_object` ADD INDEX `idx_flow_oss_object_parent_object_id` (`parent_object_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_object'
    AND INDEX_NAME = 'idx_flow_oss_object_extract_status');
SET @ddl := IF(@idx = 0,
  'ALTER TABLE `flow_oss_object` ADD INDEX `idx_flow_oss_object_extract_status` (`extract_status`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
