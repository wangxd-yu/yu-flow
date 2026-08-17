-- 2026-08-14 增量：OSS 上传场景下载可见范围（普通用户仅本人 / 运营看全部）

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'download_scope'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `download_scope` varchar(32) DEFAULT ''HOST'' COMMENT ''下载可见范围：OWNER_AND_ADMIN / ALL / OWNER_ONLY / HOST'' AFTER `caller_policy`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'download_admin_user_types'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `download_admin_user_types` varchar(512) COMMENT ''OWNER_AND_ADMIN 时看全部的宿主用户类型，逗号分隔'' AFTER `download_scope`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
