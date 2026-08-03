-- MQ 任务：敏感报文策略 / 重试 / 死信字段 + 全局 MQ_LOG_PAYLOAD_MODE 种子

ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS log_payload_mode VARCHAR(16) DEFAULT 'SYSTEM_DEFAULT';
COMMENT ON COLUMN flow_mq_task_info.log_payload_mode IS '原始报文落库策略：SYSTEM_DEFAULT/FULL/MASK/OFF';

ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS retry_max INTEGER DEFAULT 0;
COMMENT ON COLUMN flow_mq_task_info.retry_max IS '失败重试次数（0=不重试）';

ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS retry_backoff_ms INTEGER DEFAULT 1000;
COMMENT ON COLUMN flow_mq_task_info.retry_backoff_ms IS '重试间隔毫秒';

ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS dead_letter_topic VARCHAR(255) DEFAULT NULL;
COMMENT ON COLUMN flow_mq_task_info.dead_letter_topic IS '最终失败时转发的死信 topic/队列';

INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, sort_order, create_time, update_time)
VALUES ('MQ_LOG_PAYLOAD_MODE', 'FULL', 'ENUM', 'LOG', '[FULL:明文|MASK:脱敏占位|OFF:不存报文] MQ 消费日志原始报文全局默认策略。任务设为「继承全局」时生效。', 1, 1, 16, NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;
