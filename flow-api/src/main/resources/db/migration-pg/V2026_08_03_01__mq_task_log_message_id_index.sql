-- MQ 日志按 message_id 精确检索：补普通索引（存量环境幂等）
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_message_id ON flow_mq_task_log (message_id);
