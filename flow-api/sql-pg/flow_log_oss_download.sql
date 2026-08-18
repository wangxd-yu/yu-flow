-- Table: flow_log_oss_download
-- OSS 隐私下载审计
CREATE TABLE IF NOT EXISTS flow_log_oss_download (
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
COMMENT ON TABLE flow_log_oss_download IS 'OSS 隐私下载审计';
COMMENT ON COLUMN flow_log_oss_download.id IS '雪花ID';
COMMENT ON COLUMN flow_log_oss_download.object_id IS '台账 ID';
COMMENT ON COLUMN flow_log_oss_download.downloaded_by IS '下载人 userId';
COMMENT ON COLUMN flow_log_oss_download.downloaded_by_name IS '下载人展示名';
COMMENT ON COLUMN flow_log_oss_download.client_ip IS '客户端 IP';
COMMENT ON COLUMN flow_log_oss_download.user_agent IS 'User-Agent';
COMMENT ON COLUMN flow_log_oss_download.result IS 'SUCCESS / DENIED / NOT_FOUND / ERROR';
COMMENT ON COLUMN flow_log_oss_download.deny_reason IS '拒绝原因';
COMMENT ON COLUMN flow_log_oss_download.time_ms IS '耗时毫秒';
COMMENT ON COLUMN flow_log_oss_download.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_oss_download_object_id ON flow_log_oss_download (object_id);
CREATE INDEX IF NOT EXISTS idx_flow_log_oss_download_create_time ON flow_log_oss_download (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_oss_download_result ON flow_log_oss_download (result);
