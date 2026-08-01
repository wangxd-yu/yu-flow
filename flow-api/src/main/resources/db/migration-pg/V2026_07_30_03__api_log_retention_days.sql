-- API 级日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS log_retention_days integer;
COMMENT ON COLUMN flow_api_info.log_retention_days IS '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数';
