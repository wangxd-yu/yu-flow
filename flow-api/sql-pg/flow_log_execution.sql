-- Table: flow_log_execution
-- API执行日志表
CREATE TABLE IF NOT EXISTS flow_log_execution (
  id varchar(32) NOT NULL,
  api_id varchar(32),
  api_name varchar(128),
  url varchar(255),
  method varchar(16),
  request_params text,
  response_body text,
  status varchar(16),
  error_msg text,
  cost_time_ms bigint,
  trace_data text,
  service_type varchar(32),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_execution IS 'API执行日志表';
COMMENT ON COLUMN flow_log_execution.id IS '主键ID';
COMMENT ON COLUMN flow_log_execution.api_id IS 'API ID';
COMMENT ON COLUMN flow_log_execution.api_name IS 'API名称';
COMMENT ON COLUMN flow_log_execution.url IS '请求URL';
COMMENT ON COLUMN flow_log_execution.method IS '请求方法';
COMMENT ON COLUMN flow_log_execution.request_params IS '请求参数快照(JSON)';
COMMENT ON COLUMN flow_log_execution.response_body IS '返回结果快照(JSON)';
COMMENT ON COLUMN flow_log_execution.status IS '执行状态: SUCCESS/ERROR';
COMMENT ON COLUMN flow_log_execution.cost_time_ms IS '耗时(毫秒)';
COMMENT ON COLUMN flow_log_execution.trace_data IS '完整追踪快照(如有)';
COMMENT ON COLUMN flow_log_execution.service_type IS '接口类型: FLOW/DB/JSON/STRING';
COMMENT ON COLUMN flow_log_execution.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_execution_api_id ON flow_log_execution (api_id);
CREATE INDEX IF NOT EXISTS idx_flow_log_execution_create_time ON flow_log_execution (create_time);
