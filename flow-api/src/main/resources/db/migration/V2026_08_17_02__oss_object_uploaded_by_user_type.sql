-- 2026-08-17：OSS 台账落库上传人 userType，与 uploaded_by 组成本人/配额匹配键
-- 与 db/migration-pg/V2026_08_17_02__oss_object_uploaded_by_user_type.sql 语义等价

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_object'
    AND COLUMN_NAME = 'uploaded_by_user_type'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE `flow_oss_object` ADD COLUMN `uploaded_by_user_type` varchar(32) COMMENT ''上传人 userType：ADMIN / END_USER / OPEN_APP（宿主可扩展）'' AFTER `uploaded_by`',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_object'
    AND INDEX_NAME = 'idx_flow_oss_object_uploaded_by'
);
SET @ddl := IF(
    @old_idx > 0,
    'ALTER TABLE `flow_oss_object` DROP INDEX `idx_flow_oss_object_uploaded_by`',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @new_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_object'
    AND INDEX_NAME = 'idx_flow_oss_object_uploaded_by_uploaded_by_user_type'
);
SET @ddl := IF(
    @new_idx = 0,
    'ALTER TABLE `flow_oss_object` ADD INDEX `idx_flow_oss_object_uploaded_by_uploaded_by_user_type` (`uploaded_by`, `uploaded_by_user_type`)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
