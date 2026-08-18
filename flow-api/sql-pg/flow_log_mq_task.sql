-- Table: flow_log_mq_task
-- MQ 任务执行日志
CREATE TABLE IF NOT EXISTS flow_log_mq_task (
  id varchar(32) NOT NULL,
  task_id varchar(32) NOT NULL,
  task_name varchar(128),
  topic varchar(255),
  message_id varchar(255),
  trigger_type varchar(16) NOT NULL DEFAULT 'MQ',
  status varchar(16) NOT NULL,
  cost_time_ms bigint,
  error_msg text,
  message_body text,
  message_headers text,
  trace_data text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_mq_task IS 'MQ 任务执行日志';
COMMENT ON COLUMN flow_log_mq_task.id IS '雪花ID';
COMMENT ON COLUMN flow_log_mq_task.task_id IS '关联 MQ 任务ID';
COMMENT ON COLUMN flow_log_mq_task.task_name IS '任务名称（冗余）';
COMMENT ON COLUMN flow_log_mq_task.topic IS '消息 topic / 队列名';
COMMENT ON COLUMN flow_log_mq_task.message_id IS '消息ID（幂等去重键）';
COMMENT ON COLUMN flow_log_mq_task.trigger_type IS '触发类型：MQ=消息触发, MANUAL=手动';
COMMENT ON COLUMN flow_log_mq_task.status IS '执行状态：SUCCESS / FAILED / SKIPPED / RUNNING';
COMMENT ON COLUMN flow_log_mq_task.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_log_mq_task.error_msg IS '失败信息';
COMMENT ON COLUMN flow_log_mq_task.message_body IS '原始消息体（JSON 解析前的字符串，支持超限截断）';
COMMENT ON COLUMN flow_log_mq_task.message_headers IS '消息头 JSON 字典';
COMMENT ON COLUMN flow_log_mq_task.trace_data IS 'FlowTrace JSON 快照（logMode=ALL 时记录）';
COMMENT ON COLUMN flow_log_mq_task.create_time IS '执行开始时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_mq_task_create_time ON flow_log_mq_task (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_mq_task_status ON flow_log_mq_task (status);
CREATE INDEX IF NOT EXISTS idx_flow_log_mq_task_task_id ON flow_log_mq_task (task_id);
CREATE INDEX IF NOT EXISTS idx_flow_log_mq_task_message_id ON flow_log_mq_task (message_id);
