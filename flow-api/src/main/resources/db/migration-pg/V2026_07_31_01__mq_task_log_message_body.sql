-- PostgreSQL: flow_mq_task_log 新增 原始消息体 与 消息头 列
ALTER TABLE flow_mq_task_log
    ADD COLUMN IF NOT EXISTS message_body TEXT,
    ADD COLUMN IF NOT EXISTS message_headers TEXT;

COMMENT ON COLUMN flow_mq_task_log.message_body IS '原始消息体（JSON 解析前的字符串，支持超限截断）';
COMMENT ON COLUMN flow_mq_task_log.message_headers IS '消息头 JSON 字典';
