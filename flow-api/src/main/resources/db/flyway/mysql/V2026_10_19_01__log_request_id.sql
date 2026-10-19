-- 2026-10-19 增量：五张执行类日志表加 request_id（对应响应头 X-Request-Id）
-- 用新版 00_all 初始化的库已经有这一列，登记基线后仍会执行本脚本，所以每一步先查 information_schema，可重复执行

-- flow_log_execution
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `flow_log_execution` ADD COLUMN `request_id` varchar(64) COMMENT ''请求ID（响应头 X-Request-Id）'' AFTER `service_type`',
  'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_execution' AND COLUMN_NAME = 'request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_flow_log_execution_request_id` ON `flow_log_execution` (`request_id`)',
  'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_execution' AND INDEX_NAME = 'idx_flow_log_execution_request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- flow_log_third
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `flow_log_third` ADD COLUMN `request_id` varchar(64) COMMENT ''请求ID（响应头 X-Request-Id）'' AFTER `curl`',
  'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_third' AND COLUMN_NAME = 'request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_flow_log_third_request_id` ON `flow_log_third` (`request_id`)',
  'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_third' AND INDEX_NAME = 'idx_flow_log_third_request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- flow_log_task
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `flow_log_task` ADD COLUMN `request_id` varchar(64) COMMENT ''请求ID（响应头 X-Request-Id）'' AFTER `trace_data`',
  'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_task' AND COLUMN_NAME = 'request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_flow_log_task_request_id` ON `flow_log_task` (`request_id`)',
  'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_task' AND INDEX_NAME = 'idx_flow_log_task_request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- flow_log_service
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `flow_log_service` ADD COLUMN `request_id` varchar(64) COMMENT ''请求ID（响应头 X-Request-Id）'' AFTER `trace_data`',
  'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_service' AND COLUMN_NAME = 'request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_flow_log_service_request_id` ON `flow_log_service` (`request_id`)',
  'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_service' AND INDEX_NAME = 'idx_flow_log_service_request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- flow_log_mq_task
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `flow_log_mq_task` ADD COLUMN `request_id` varchar(64) COMMENT ''请求ID（响应头 X-Request-Id）'' AFTER `trace_data`',
  'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_mq_task' AND COLUMN_NAME = 'request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_flow_log_mq_task_request_id` ON `flow_log_mq_task` (`request_id`)',
  'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_mq_task' AND INDEX_NAME = 'idx_flow_log_mq_task_request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
