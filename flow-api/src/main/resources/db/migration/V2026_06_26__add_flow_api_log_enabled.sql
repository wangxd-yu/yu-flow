-- 为动态 API 增加执行日志开关。历史数据默认开启，兼容现有行为。

ALTER TABLE flow_api_info
    ADD COLUMN log_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否记录执行日志：1-开启，0-关闭';
