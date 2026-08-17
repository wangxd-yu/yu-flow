-- 2026-08-06 增量：OSS 预签名直传（单 PUT）场景级开关
-- 默认 0（关闭）：存量场景保持仅走网关代理上传，需显式开启才允许客户端 PUT 直达 OSS

-- =============================================================================
-- 新增 presign_upload_enabled 列
-- =============================================================================
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_oss_upload_profile'
    AND COLUMN_NAME = 'presign_upload_enabled'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_oss_upload_profile` ADD COLUMN `presign_upload_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否开放预签名直传：0=仅网关代理上传, 1=允许客户端 PUT 直达 OSS'' AFTER `require_auth`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================================
-- flow_oss_object.status 追加 PENDING 语义（仅刷新列注释，取值不受约束限制）
-- =============================================================================
ALTER TABLE `flow_oss_object`
  MODIFY COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
  COMMENT 'ACTIVE / PENDING（预签名待确认）/ DELETED';
