-- Table: flow_alert_event
-- 告警事件历史
CREATE TABLE IF NOT EXISTS flow_alert_event (
  id varchar(64) NOT NULL,
  rule_id varchar(64),
  rule_name varchar(100),
  fingerprint varchar(200),
  asset_type varchar(32),
  asset_id varchar(64),
  asset_name varchar(200),
  health varchar(20),
  error_rate double precision,
  fail_count bigint,
  "window" varchar(20),
  channel_type varchar(20),
  channel_id varchar(64),
  status varchar(20) NOT NULL,
  payload_json text,
  error_msg varchar(500),
  fired_at timestamp NOT NULL,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_event IS '告警事件历史';
COMMENT ON COLUMN flow_alert_event.id IS '主键';
COMMENT ON COLUMN flow_alert_event.rule_id IS '规则 ID，SysConfig 兜底为空';
COMMENT ON COLUMN flow_alert_event.fingerprint IS '去重指纹';
COMMENT ON COLUMN flow_alert_event."window" IS '1h/24h/7d';
COMMENT ON COLUMN flow_alert_event.status IS 'SUCCESS|FAIL|SUPPRESSED';
CREATE INDEX IF NOT EXISTS idx_flow_alert_event_fired_at ON flow_alert_event (fired_at);
CREATE INDEX IF NOT EXISTS idx_flow_alert_event_rule_id ON flow_alert_event (rule_id);
CREATE INDEX IF NOT EXISTS idx_flow_alert_event_status ON flow_alert_event (status);
