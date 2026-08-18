-- 2026-08-18：告警事件历史对齐流水表 flow_log_* 前缀
-- flow_alert_event → flow_log_alert
-- 与 db/migration/V2026_08_18_02__rename_alert_event_to_flow_log_alert.sql 语义等价
-- 新环境 00_all 已是新表名，旧表不存在时跳过
-- 现网索引可能是历史短名 idx_alert_event_* 或规范名 idx_flow_alert_event_*

DO $$
BEGIN
  IF to_regclass('flow_alert_event') IS NOT NULL AND to_regclass('flow_log_alert') IS NULL THEN
    ALTER TABLE flow_alert_event RENAME TO flow_log_alert;
  END IF;

  IF to_regclass('idx_alert_event_fired') IS NOT NULL THEN
    ALTER INDEX idx_alert_event_fired RENAME TO idx_flow_log_alert_fired_at;
  END IF;
  IF to_regclass('idx_flow_alert_event_fired_at') IS NOT NULL THEN
    ALTER INDEX idx_flow_alert_event_fired_at RENAME TO idx_flow_log_alert_fired_at;
  END IF;

  IF to_regclass('idx_alert_event_rule') IS NOT NULL THEN
    ALTER INDEX idx_alert_event_rule RENAME TO idx_flow_log_alert_rule_id;
  END IF;
  IF to_regclass('idx_flow_alert_event_rule_id') IS NOT NULL THEN
    ALTER INDEX idx_flow_alert_event_rule_id RENAME TO idx_flow_log_alert_rule_id;
  END IF;

  IF to_regclass('idx_alert_event_status') IS NOT NULL THEN
    ALTER INDEX idx_alert_event_status RENAME TO idx_flow_log_alert_status;
  END IF;
  IF to_regclass('idx_flow_alert_event_status') IS NOT NULL THEN
    ALTER INDEX idx_flow_alert_event_status RENAME TO idx_flow_log_alert_status;
  END IF;

  IF to_regclass('flow_log_alert') IS NOT NULL THEN
    COMMENT ON TABLE flow_log_alert IS '告警事件历史';
  END IF;
END $$;

UPDATE flow_sys_config
SET remark = '告警历史事件保留天数（flow_log_alert；0 = 不清理）'
WHERE config_key = 'LOG_ALERT_EVENT_RETENTION_DAYS';
