-- 2026-08-18：告警事件历史对齐流水表 flow_log_* 前缀
-- flow_alert_event → flow_log_alert
-- 与 db/migration-pg/V2026_08_18_02__rename_alert_event_to_flow_log_alert.sql 语义等价
-- 新环境 00_all 已是新表名，旧表不存在时跳过
-- 现网索引可能是历史短名 idx_alert_event_* 或规范名 idx_flow_alert_event_*

SET @old := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_alert_event'
);
SET @ddl := IF(@old > 0, 'RENAME TABLE `flow_alert_event` TO `flow_log_alert`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_alert'
    AND INDEX_NAME = 'idx_alert_event_fired'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_alert` RENAME INDEX `idx_alert_event_fired` TO `idx_flow_log_alert_fired_at`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_alert'
    AND INDEX_NAME = 'idx_flow_alert_event_fired_at'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_alert` RENAME INDEX `idx_flow_alert_event_fired_at` TO `idx_flow_log_alert_fired_at`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_alert'
    AND INDEX_NAME = 'idx_alert_event_rule'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_alert` RENAME INDEX `idx_alert_event_rule` TO `idx_flow_log_alert_rule_id`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_alert'
    AND INDEX_NAME = 'idx_flow_alert_event_rule_id'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_alert` RENAME INDEX `idx_flow_alert_event_rule_id` TO `idx_flow_log_alert_rule_id`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_alert'
    AND INDEX_NAME = 'idx_alert_event_status'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_alert` RENAME INDEX `idx_alert_event_status` TO `idx_flow_log_alert_status`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_alert'
    AND INDEX_NAME = 'idx_flow_alert_event_status'
);
SET @ddl := IF(@idx > 0, 'ALTER TABLE `flow_log_alert` RENAME INDEX `idx_flow_alert_event_status` TO `idx_flow_log_alert_status`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `flow_sys_config`
SET `remark` = '告警历史事件保留天数（flow_log_alert；0 = 不清理）'
WHERE `config_key` = 'LOG_ALERT_EVENT_RETENTION_DAYS';
