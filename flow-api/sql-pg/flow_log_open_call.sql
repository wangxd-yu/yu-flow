-- Table: flow_log_open_call
-- 开放平台入站调用摘要
CREATE TABLE IF NOT EXISTS flow_log_open_call (
  id varchar(32) NOT NULL,
  platform_id varchar(32),
  app_key varchar(64),
  api_id varchar(32),
  method varchar(16),
  path varchar(512),
  status integer,
  cost_ms bigint,
  error_code varchar(64),
  request_id varchar(64),
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_open_call IS '开放平台入站调用摘要';
COMMENT ON COLUMN flow_log_open_call.id IS '雪花ID';
COMMENT ON COLUMN flow_log_open_call.platform_id IS '开放平台ID';
COMMENT ON COLUMN flow_log_open_call.app_key IS '调用方 AppKey';
COMMENT ON COLUMN flow_log_open_call.api_id IS '命中的 API ID';
COMMENT ON COLUMN flow_log_open_call.method IS 'HTTP 方法';
COMMENT ON COLUMN flow_log_open_call.path IS '真实发布路径';
COMMENT ON COLUMN flow_log_open_call.status IS 'HTTP 状态';
COMMENT ON COLUMN flow_log_open_call.cost_ms IS '耗时毫秒';
COMMENT ON COLUMN flow_log_open_call.error_code IS '业务/鉴权错误码';
COMMENT ON COLUMN flow_log_open_call.request_id IS '请求追踪ID';
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_app_key ON flow_log_open_call (app_key);
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_create_time ON flow_log_open_call (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_open_call_platform_id_create_time ON flow_log_open_call (platform_id, create_time);
