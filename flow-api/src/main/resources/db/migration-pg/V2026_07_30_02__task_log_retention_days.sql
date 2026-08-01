-- 任务级日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数
ALTER TABLE flow_task_info ADD COLUMN IF NOT EXISTS log_retention_days integer;
COMMENT ON COLUMN flow_task_info.log_retention_days IS '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数';

ALTER TABLE flow_mq_task_info ADD COLUMN IF NOT EXISTS log_retention_days integer;
COMMENT ON COLUMN flow_mq_task_info.log_retention_days IS '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数';
