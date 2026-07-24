-- Table: flow_metrics_minute
-- 资产运行计量分钟汇总
CREATE TABLE IF NOT EXISTS `flow_metrics_minute` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `asset_type` varchar(16) NOT NULL COMMENT 'API / TASK / SERVICE',
  `asset_id` varchar(32) NOT NULL COMMENT '资产ID',
  `trigger_type` varchar(16) NOT NULL DEFAULT '_' COMMENT '触发类型；API 固定为 _',
  `bucket_start` datetime NOT NULL COMMENT '分钟桶起点（整分）',
  `success_cnt` bigint NOT NULL DEFAULT 0,
  `fail_cnt` bigint NOT NULL DEFAULT 0,
  `auth_fail_cnt` bigint NOT NULL DEFAULT 0 COMMENT '鉴权失败次数（如开放平台 401/403）',
  `skipped_cnt` bigint NOT NULL DEFAULT 0,
  `sum_cost_ms` bigint NOT NULL DEFAULT 0,
  `latency_count` bigint NOT NULL DEFAULT 0,
  `hist_json` varchar(1024) COMMENT '直方图桶计数 JSON 数组',
  `update_time` datetime COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_metrics_minute_bucket_start` (`bucket_start`),
  KEY `idx_flow_metrics_minute_asset_type_bucket_start` (`asset_type`, `bucket_start`),
  UNIQUE KEY `uk_flow_metrics_minute_asset_type_asset_id_trigger_type_buck_967` (`asset_type`, `asset_id`, `trigger_type`, `bucket_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量分钟汇总';
