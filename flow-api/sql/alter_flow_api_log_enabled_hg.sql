-- 为动态 API 增加执行日志开关（瀚高 / PostgreSQL 版本）。
-- 历史数据默认开启，兼容现有行为。

ALTER TABLE flow_api_info
    ADD COLUMN IF NOT EXISTS log_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN flow_api_info.log_enabled IS '是否记录执行日志：true-开启，false-关闭';
