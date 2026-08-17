-- 目录：path_prefix / security_config / remark
-- 与 db/migration-pg/V2026_08_12_01__directory_path_prefix_security.sql 语义等价
-- 三列均可空：存量目录为 NULL 时行为与改前一致

-- path_prefix
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_directory'
    AND COLUMN_NAME = 'path_prefix'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_directory` ADD COLUMN `path_prefix` VARCHAR(256) NULL COMMENT ''URL路径前缀，可空；新建接口默认继承'' AFTER `sort`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- security_config
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_directory'
    AND COLUMN_NAME = 'security_config'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_directory` ADD COLUMN `security_config` TEXT NULL COMMENT ''目录级入站防护JSON，结构同ApiSecurityConfig'' AFTER `path_prefix`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- remark
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_directory'
    AND COLUMN_NAME = 'remark'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_directory` ADD COLUMN `remark` VARCHAR(512) NULL COMMENT ''备注'' AFTER `security_config`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
