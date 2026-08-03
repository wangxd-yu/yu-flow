-- Table: flow_oss_download_log
CREATE TABLE IF NOT EXISTS flow_oss_download_log (
  id varchar(32) NOT NULL,
  object_id varchar(32),
  downloaded_by varchar(64),
  downloaded_by_name varchar(128),
  client_ip varchar(64),
  user_agent varchar(512),
  result varchar(16),
  deny_reason varchar(512),
  time_ms bigint,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_download_log IS 'OSS 隐私下载审计';
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_object_id ON flow_oss_download_log (object_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_create_time ON flow_oss_download_log (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_oss_download_log_result ON flow_oss_download_log (result);
