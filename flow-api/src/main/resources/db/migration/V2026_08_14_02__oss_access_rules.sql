-- 2026-08-14：OSS 访问规则改挂 caller_policy.rules，去掉场景级 download_scope

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'download_admin_user_types'
);
SET @ddl := IF(
  @col_exists > 0,
  'ALTER TABLE `flow_oss_upload_profile` DROP COLUMN `download_admin_user_types`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'download_scope'
);
SET @ddl := IF(
  @col_exists > 0,
  'ALTER TABLE `flow_oss_upload_profile` DROP COLUMN `download_scope`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
