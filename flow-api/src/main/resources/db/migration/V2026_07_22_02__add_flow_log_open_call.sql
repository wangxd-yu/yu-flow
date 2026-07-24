-- 开放平台入站调用摘要日志
CREATE TABLE IF NOT EXISTS `flow_log_open_call` (
    `id`           VARCHAR(32)  NOT NULL COMMENT '雪花ID',
    `platform_id`  VARCHAR(32)           COMMENT '开放平台ID',
    `app_key`      VARCHAR(64)           COMMENT '调用方 AppKey',
    `api_id`       VARCHAR(32)           COMMENT '命中的 API ID',
    `method`       VARCHAR(16)           COMMENT 'HTTP 方法',
    `path`         VARCHAR(512)          COMMENT '真实发布路径',
    `status`       INT                   COMMENT 'HTTP 状态',
    `cost_ms`      BIGINT                COMMENT '耗时毫秒',
    `error_code`   VARCHAR(64)           COMMENT '业务/鉴权错误码',
    `request_id`   VARCHAR(64)           COMMENT '请求追踪ID',
    `create_time`  DATETIME,
    PRIMARY KEY (`id`),
    KEY `idx_flow_log_open_call_platform_time` (`platform_id`, `create_time`),
    KEY `idx_flow_log_open_call_app_key` (`app_key`),
    KEY `idx_flow_log_open_call_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台入站调用摘要';

-- 平台级调用日志开关（1=开启，0=关闭；空按全局默认）
ALTER TABLE `flow_open_platform`
    ADD COLUMN `open_call_log_enabled` TINYINT NULL DEFAULT 1 COMMENT '是否记录入站摘要日志 0关1开' AFTER `expire_at`;
