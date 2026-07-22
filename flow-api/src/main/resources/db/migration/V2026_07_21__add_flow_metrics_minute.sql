-- 资产运行计量：分钟级汇总（温层）
-- 索引名带表前缀，便于迁移 PostgreSQL（schema 内索引名全局唯一）
CREATE TABLE IF NOT EXISTS `flow_metrics_minute` (
    `id`              VARCHAR(32)    NOT NULL COMMENT '雪花ID',
    `asset_type`      VARCHAR(16)    NOT NULL COMMENT 'API / TASK / SERVICE',
    `asset_id`        VARCHAR(32)    NOT NULL COMMENT '资产ID',
    `trigger_type`    VARCHAR(16)    NOT NULL DEFAULT '_' COMMENT '触发类型；API 固定为 _',
    `bucket_start`    DATETIME       NOT NULL COMMENT '分钟桶起点（整分）',
    `success_cnt`     BIGINT         NOT NULL DEFAULT 0,
    `fail_cnt`        BIGINT         NOT NULL DEFAULT 0,
    `skipped_cnt`     BIGINT         NOT NULL DEFAULT 0,
    `sum_cost_ms`     BIGINT         NOT NULL DEFAULT 0,
    `latency_count`   BIGINT         NOT NULL DEFAULT 0,
    `hist_json`       VARCHAR(1024)           COMMENT '直方图桶计数 JSON 数组',
    `update_time`     DATETIME                COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_metrics_minute_asset_bucket` (`asset_type`, `asset_id`, `trigger_type`, `bucket_start`),
    KEY `idx_metrics_minute_bucket_start` (`bucket_start`),
    KEY `idx_metrics_minute_type_bucket` (`asset_type`, `bucket_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量分钟汇总';
