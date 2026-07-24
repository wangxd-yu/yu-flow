-- Table: flow_alert_channel
-- 告警通道
CREATE TABLE IF NOT EXISTS flow_alert_channel (
  id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  type varchar(20) NOT NULL,
  config_json text,
  enabled smallint NOT NULL DEFAULT 1,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_alert_channel IS '告警通道';
COMMENT ON COLUMN flow_alert_channel.id IS '主键';
COMMENT ON COLUMN flow_alert_channel.name IS '通道名称';
COMMENT ON COLUMN flow_alert_channel.type IS 'WEBHOOK | EMAIL';
COMMENT ON COLUMN flow_alert_channel.config_json IS '通道配置 JSON';
COMMENT ON COLUMN flow_alert_channel.enabled IS '1启用 0停用';
CREATE INDEX IF NOT EXISTS idx_flow_alert_channel_type ON flow_alert_channel (type);
