-- Table: flow_log_task
-- 定时任务执行日志
CREATE TABLE IF NOT EXISTS flow_log_task (
  id varchar(32) NOT NULL,
  task_id varchar(32) NOT NULL,
  task_name varchar(128),
  trigger_type varchar(16) NOT NULL DEFAULT 'CRON',
  status varchar(16) NOT NULL,
  cost_time_ms bigint,
  error_msg text,
  trace_data text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_task IS '定时任务执行日志';
COMMENT ON COLUMN flow_log_task.id IS '雪花ID';
COMMENT ON COLUMN flow_log_task.task_id IS '关联任务ID';
COMMENT ON COLUMN flow_log_task.task_name IS '任务名称（冗余）';
COMMENT ON COLUMN flow_log_task.trigger_type IS '触发类型：CRON=定时, MANUAL=手动';
COMMENT ON COLUMN flow_log_task.status IS '执行状态：SUCCESS / FAILED / RUNNING';
COMMENT ON COLUMN flow_log_task.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_log_task.error_msg IS '失败信息';
COMMENT ON COLUMN flow_log_task.trace_data IS 'FlowTrace JSON 快照（logEnabled=true 时记录）';
COMMENT ON COLUMN flow_log_task.create_time IS '执行开始时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_task_create_time ON flow_log_task (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_task_status ON flow_log_task (status);
CREATE INDEX IF NOT EXISTS idx_flow_log_task_task_id ON flow_log_task (task_id);
