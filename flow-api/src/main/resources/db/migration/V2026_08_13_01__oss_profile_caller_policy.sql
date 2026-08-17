-- 2026-08-13 增量：OSS 上传场景宿主调用方策略（upload/download CallerPolicy JSON）

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'caller_policy'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `caller_policy` text COMMENT ''宿主调用方策略 JSON：{"upload":CallerPolicy,"download":CallerPolicy}'' AFTER `download_perm`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
