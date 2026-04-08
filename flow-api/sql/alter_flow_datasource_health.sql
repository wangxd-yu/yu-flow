-- ============================================================
-- 为 flow_datasource 表新增健康度追踪字段
-- health_status: 连接健康状态 HEALTHY/UNHEALTHY/UNKNOWN
-- error_count:   连续失败次数
-- last_error_msg: 最后一次失败的异常信息
-- ============================================================

ALTER TABLE flow_datasource
    ADD COLUMN `health_status` VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'
        COMMENT '连接健康度：HEALTHY-健康, UNHEALTHY-异常, UNKNOWN-未知';

ALTER TABLE flow_datasource
    ADD COLUMN `error_count` INT NOT NULL DEFAULT 0
        COMMENT '连续连接失败次数';

ALTER TABLE flow_datasource
    ADD COLUMN `last_error_msg` TEXT DEFAULT NULL
        COMMENT '最后一次连接失败的异常堆栈/简述';
