-- Table: flow_mq_task_log
-- MQ 任务执行日志
CREATE TABLE IF NOT EXISTS flow_mq_task_log (
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
COMMENT ON TABLE flow_mq_task_log IS 'MQ 任务执行日志';
COMMENT ON COLUMN flow_mq_task_log.id IS '雪花ID';
COMMENT ON COLUMN flow_mq_task_log.task_id IS '关联 MQ 任务ID';
COMMENT ON COLUMN flow_mq_task_log.task_name IS '任务名称（冗余）';
COMMENT ON COLUMN flow_mq_task_log.topic IS '消息 topic / 队列名';
COMMENT ON COLUMN flow_mq_task_log.message_id IS '消息ID（幂等去重键）';
COMMENT ON COLUMN flow_mq_task_log.trigger_type IS '触发类型：MQ=消息触发, MANUAL=手动';
COMMENT ON COLUMN flow_mq_task_log.status IS '执行状态：SUCCESS / FAILED / SKIPPED / RUNNING';
COMMENT ON COLUMN flow_mq_task_log.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_mq_task_log.error_msg IS '失败信息';
COMMENT ON COLUMN flow_mq_task_log.message_body IS '原始消息体（JSON 解析前的字符串，支持超限截断）';
COMMENT ON COLUMN flow_mq_task_log.message_headers IS '消息头 JSON 字典';
COMMENT ON COLUMN flow_mq_task_log.trace_data IS 'FlowTrace JSON 快照（logMode=ALL 时记录）';
COMMENT ON COLUMN flow_mq_task_log.create_time IS '执行开始时间';
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_create_time ON flow_mq_task_log (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_status ON flow_mq_task_log (status);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_task_id ON flow_mq_task_log (task_id);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_message_id ON flow_mq_task_log (message_id);
