-- Table: flow_log_audit
-- 配置变更审计
CREATE TABLE IF NOT EXISTS flow_log_audit (
  id varchar(64) NOT NULL,
  action varchar(64) NOT NULL,
  operator varchar(100),
  target_type varchar(64),
  target_id varchar(64),
  detail varchar(1024),
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_audit IS '配置变更审计';
COMMENT ON COLUMN flow_log_audit.action IS 'API_PUBLISH|SYS_CONFIG_UPDATE|OPEN_SECRET_ROTATE';
COMMENT ON COLUMN flow_log_audit.operator IS '操作人';
COMMENT ON COLUMN flow_log_audit.target_type IS 'API|SYS_CONFIG|OPEN_CREDENTIAL';
COMMENT ON COLUMN flow_log_audit.detail IS 'JSON 摘要，不含密钥';
CREATE INDEX IF NOT EXISTS idx_flow_log_audit_action_create_time ON flow_log_audit (action, create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_audit_operator ON flow_log_audit (operator);
