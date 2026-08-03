-- MQ 任务：敏感报文策略 / 重试 / 死信字段 + 全局 MQ_LOG_PAYLOAD_MODE 种子

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_info'
    AND COLUMN_NAME = 'log_payload_mode'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_mq_task_info` ADD COLUMN `log_payload_mode` VARCHAR(16) DEFAULT ''SYSTEM_DEFAULT'' COMMENT ''原始报文落库策略：SYSTEM_DEFAULT/FULL/MASK/OFF''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_info'
    AND COLUMN_NAME = 'retry_max'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_mq_task_info` ADD COLUMN `retry_max` INT DEFAULT 0 COMMENT ''失败重试次数（0=不重试）''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_info'
    AND COLUMN_NAME = 'retry_backoff_ms'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_mq_task_info` ADD COLUMN `retry_backoff_ms` INT DEFAULT 1000 COMMENT ''重试间隔毫秒''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_mq_task_info'
    AND COLUMN_NAME = 'dead_letter_topic'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE `flow_mq_task_info` ADD COLUMN `dead_letter_topic` VARCHAR(255) DEFAULT NULL COMMENT ''最终失败时转发的死信 topic/队列''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, sort_order, create_time, update_time)
VALUES ('MQ_LOG_PAYLOAD_MODE', 'FULL', 'ENUM', 'LOG', '[FULL:明文|MASK:脱敏占位|OFF:不存报文] MQ 消费日志原始报文全局默认策略。任务设为「继承全局」时生效。', 1, 1, 16, NOW(), NOW())
ON DUPLICATE KEY UPDATE config_value = config_value;
