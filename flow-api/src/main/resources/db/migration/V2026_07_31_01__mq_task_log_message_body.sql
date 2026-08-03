-- MySQL: flow_mq_task_log 新增 原始消息体 与 消息头 列
ALTER TABLE flow_mq_task_log
    ADD COLUMN message_body LONGTEXT COMMENT '原始消息体（JSON 解析前的字符串，支持超限截断）',
    ADD COLUMN message_headers TEXT COMMENT '消息头 JSON 字典';
