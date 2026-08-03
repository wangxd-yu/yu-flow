-- MQ 日志按 message_id 精确检索：补普通索引（存量环境幂等）
-- 说明：勿改写已执行的 V2026_07_31_01；列已由该版本落地，此处仅加索引。

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_log'
    AND INDEX_NAME = 'idx_flow_mq_task_log_message_id'
);
SET @ddl := IF(
  @idx_exists = 0,
  'ALTER TABLE `flow_mq_task_log` ADD INDEX `idx_flow_mq_task_log_message_id` (`message_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
