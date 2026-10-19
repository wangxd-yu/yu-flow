-- 2026-10-19 增量：五张执行类日志表加 request_id（对应响应头 X-Request-Id）
-- 可重复执行。瀚高不走 Flyway，按版本号顺序手工执行本目录的脚本

ALTER TABLE flow_log_execution ADD COLUMN IF NOT EXISTS request_id varchar(64);
COMMENT ON COLUMN flow_log_execution.request_id IS '请求ID（响应头 X-Request-Id）';
CREATE INDEX IF NOT EXISTS idx_flow_log_execution_request_id ON flow_log_execution (request_id);

ALTER TABLE flow_log_third ADD COLUMN IF NOT EXISTS request_id varchar(64);
COMMENT ON COLUMN flow_log_third.request_id IS '请求ID（响应头 X-Request-Id）';
CREATE INDEX IF NOT EXISTS idx_flow_log_third_request_id ON flow_log_third (request_id);

ALTER TABLE flow_log_task ADD COLUMN IF NOT EXISTS request_id varchar(64);
COMMENT ON COLUMN flow_log_task.request_id IS '请求ID（响应头 X-Request-Id）';
CREATE INDEX IF NOT EXISTS idx_flow_log_task_request_id ON flow_log_task (request_id);

ALTER TABLE flow_log_service ADD COLUMN IF NOT EXISTS request_id varchar(64);
COMMENT ON COLUMN flow_log_service.request_id IS '请求ID（响应头 X-Request-Id）';
CREATE INDEX IF NOT EXISTS idx_flow_log_service_request_id ON flow_log_service (request_id);

ALTER TABLE flow_log_mq_task ADD COLUMN IF NOT EXISTS request_id varchar(64);
COMMENT ON COLUMN flow_log_mq_task.request_id IS '请求ID（响应头 X-Request-Id）';
CREATE INDEX IF NOT EXISTS idx_flow_log_mq_task_request_id ON flow_log_mq_task (request_id);
