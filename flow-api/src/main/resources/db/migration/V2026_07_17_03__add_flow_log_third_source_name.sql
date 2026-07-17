-- 存量库补 source_name；若建表脚本已含该列则跳过
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_log_third'
    AND COLUMN_NAME = 'source_name'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_log_third` ADD COLUMN `source_name` varchar(128) DEFAULT NULL COMMENT ''来源名称（接口名/任务名）'' AFTER `source_ref`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
