-- Table: flow_alert_rule
-- 告警规则
CREATE TABLE IF NOT EXISTS flow_alert_rule (
  id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  enabled smallint NOT NULL DEFAULT 1,
  scope_asset_types varchar(200),
  "window" varchar(20) NOT NULL DEFAULT '24h',
  min_health varchar(20) NOT NULL DEFAULT 'error',
  top_n integer NOT NULL DEFAULT 10,
  channel_ids varchar(500),
  interval_minutes integer NOT NULL DEFAULT 15,
  dedup_minutes integer NOT NULL DEFAULT 60,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_rule IS '告警规则';
COMMENT ON COLUMN flow_alert_rule.id IS '主键';
COMMENT ON COLUMN flow_alert_rule.name IS '规则名称';
COMMENT ON COLUMN flow_alert_rule.enabled IS '1启用 0停用';
COMMENT ON COLUMN flow_alert_rule.scope_asset_types IS '资产类型 CSV：API,TASK,SERVICE,PLATFORM；空=全部';
COMMENT ON COLUMN flow_alert_rule."window" IS '1h/24h/7d';
COMMENT ON COLUMN flow_alert_rule.min_health IS 'error|warn';
COMMENT ON COLUMN flow_alert_rule.channel_ids IS '通道 ID JSON 数组';
CREATE INDEX IF NOT EXISTS idx_flow_alert_rule_enabled ON flow_alert_rule (enabled);
