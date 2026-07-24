-- Table: flow_log_service
-- 内部服务编排执行日志
CREATE TABLE IF NOT EXISTS flow_log_service (
  id varchar(32) NOT NULL,
  service_id varchar(32) NOT NULL,
  service_name varchar(128),
  trigger_type varchar(16) NOT NULL DEFAULT 'MANUAL',
  status varchar(16) NOT NULL,
  cost_time_ms bigint,
  error_msg text,
  trace_data text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_service IS '内部服务编排执行日志';
COMMENT ON COLUMN flow_log_service.id IS '雪花ID';
COMMENT ON COLUMN flow_log_service.service_id IS '关联服务ID';
COMMENT ON COLUMN flow_log_service.service_name IS '服务名称（冗余）';
COMMENT ON COLUMN flow_log_service.trigger_type IS '触发类型：MANUAL=手动, CALL=流程内调用, DEBUG=调试';
COMMENT ON COLUMN flow_log_service.status IS '执行状态：SUCCESS / FAILED / RUNNING';
COMMENT ON COLUMN flow_log_service.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_log_service.error_msg IS '失败信息';
COMMENT ON COLUMN flow_log_service.trace_data IS 'FlowTrace JSON 快照（logEnabled=true 时记录）';
COMMENT ON COLUMN flow_log_service.create_time IS '执行开始时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_service_create_time ON flow_log_service (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_service_service_id ON flow_log_service (service_id);
CREATE INDEX IF NOT EXISTS idx_flow_log_service_status ON flow_log_service (status);
