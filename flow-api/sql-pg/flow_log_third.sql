-- Table: flow_log_third
-- 第三方接口调用日志
CREATE TABLE IF NOT EXISTS flow_log_third (
  id varchar(64) NOT NULL,
  api_type varchar(50),
  source varchar(16),
  source_ref varchar(64),
  source_name varchar(128),
  request_url varchar(512),
  request_method varchar(10),
  request_params text,
  request_headers text,
  response_status integer,
  response_body text,
  elapsed_time bigint,
  is_success smallint,
  error_message varchar(1000),
  curl text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_third IS '第三方接口调用日志';
COMMENT ON COLUMN flow_log_third.id IS '主键';
COMMENT ON COLUMN flow_log_third.api_type IS '接口标识';
COMMENT ON COLUMN flow_log_third.source IS '调用来源：API/TASK/DEBUG/OTHER';
COMMENT ON COLUMN flow_log_third.source_ref IS '来源关联ID（apiId/taskId）';
COMMENT ON COLUMN flow_log_third.source_name IS '来源名称（接口名/任务名）';
COMMENT ON COLUMN flow_log_third.request_url IS '请求URL';
COMMENT ON COLUMN flow_log_third.request_method IS '请求方法';
COMMENT ON COLUMN flow_log_third.request_params IS '请求参数';
COMMENT ON COLUMN flow_log_third.request_headers IS '请求头';
COMMENT ON COLUMN flow_log_third.response_status IS '响应状态码';
COMMENT ON COLUMN flow_log_third.response_body IS '响应内容';
COMMENT ON COLUMN flow_log_third.elapsed_time IS '请求耗时(ms)';
COMMENT ON COLUMN flow_log_third.is_success IS '是否成功（0失败/1成功）';
COMMENT ON COLUMN flow_log_third.error_message IS '错误信息';
COMMENT ON COLUMN flow_log_third.curl IS 'Curl命令';
COMMENT ON COLUMN flow_log_third.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_third_api_type ON flow_log_third (api_type);
CREATE INDEX IF NOT EXISTS idx_flow_log_third_create_time ON flow_log_third (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_third_source ON flow_log_third (source);
