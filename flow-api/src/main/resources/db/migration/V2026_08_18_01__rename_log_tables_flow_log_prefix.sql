-- 2026-08-18：日志表对齐 flow_log_* 前缀
-- flow_mq_task_log → flow_log_mq_task
-- flow_oss_download_log → flow_log_oss_download
-- 与 db/migration-pg/V2026_08_18_01__rename_log_tables_flow_log_prefix.sql 语义等价
-- 新环境 00_all 已是新表名，旧表不存在时跳过

SET @old := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_mq_task_log'
);
SET @ddl := IF(@old > 0, 'RENAME TABLE `flow_mq_task_log` TO `flow_log_mq_task`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_mq_task'
    AND INDEX_NAME = 'idx_flow_mq_task_log_create_time'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_mq_task` RENAME INDEX `idx_flow_mq_task_log_create_time` TO `idx_flow_log_mq_task_create_time`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_mq_task'
    AND INDEX_NAME = 'idx_flow_mq_task_log_status'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_mq_task` RENAME INDEX `idx_flow_mq_task_log_status` TO `idx_flow_log_mq_task_status`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_mq_task'
    AND INDEX_NAME = 'idx_flow_mq_task_log_task_id'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_mq_task` RENAME INDEX `idx_flow_mq_task_log_task_id` TO `idx_flow_log_mq_task_task_id`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_mq_task'
    AND INDEX_NAME = 'idx_flow_mq_task_log_message_id'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_mq_task` RENAME INDEX `idx_flow_mq_task_log_message_id` TO `idx_flow_log_mq_task_message_id`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_oss_download_log'
);
SET @ddl := IF(@old > 0, 'RENAME TABLE `flow_oss_download_log` TO `flow_log_oss_download`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_oss_download'
    AND INDEX_NAME = 'idx_flow_oss_download_log_object_id'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_oss_download` RENAME INDEX `idx_flow_oss_download_log_object_id` TO `idx_flow_log_oss_download_object_id`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_oss_download'
    AND INDEX_NAME = 'idx_flow_oss_download_log_create_time'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_oss_download` RENAME INDEX `idx_flow_oss_download_log_create_time` TO `idx_flow_log_oss_download_create_time`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_oss_download'
    AND INDEX_NAME = 'idx_flow_oss_download_log_result'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_oss_download` RENAME INDEX `idx_flow_oss_download_log_result` TO `idx_flow_log_oss_download_result`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
