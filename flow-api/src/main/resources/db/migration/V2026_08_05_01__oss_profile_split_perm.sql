-- 2026-08-05 OSS 上传场景：access_perm 拆分为 upload_perm（上传权限） + download_perm（下载权限）
-- 迁移策略：原 access_perm 值复制到 upload_perm，access_perm 保留列但已废弃（不再由代码读写）

-- =============================================================================
-- 新增 upload_perm 列
-- =============================================================================
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'upload_perm'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `upload_perm` VARCHAR(128) DEFAULT NULL COMMENT ''上传权限码：哪些 RBAC 权限才能调用该场景的上传 API；留空=仅 requireAuth 控制'' AFTER `biz_fields_schema`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================================
-- 新增 download_perm 列
-- =============================================================================
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'download_perm'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `download_perm` VARCHAR(128) DEFAULT NULL COMMENT ''下载权限码：哪些 RBAC 权限可突破 DataScope 访问私有文件；留空=仅 DataScope 控制'' AFTER `upload_perm`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================================
-- 数据迁移：原 access_perm 的值迁移到 upload_perm
-- =============================================================================
UPDATE `flow_oss_upload_profile`
SET `upload_perm` = `access_perm`
WHERE `access_perm` IS NOT NULL
  AND `upload_perm` IS NULL;
