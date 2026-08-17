-- Table: flow_metrics_meta
-- 资产运行计量元数据
CREATE TABLE IF NOT EXISTS `flow_metrics_meta` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `asset_type` varchar(16) NOT NULL COMMENT 'API / TASK / MQ_TASK / SERVICE / PLATFORM / SYSTEM',
  `asset_id` varchar(32) NOT NULL COMMENT '资产ID',
  `last_success_at` bigint COMMENT '最近成功 epoch ms',
  `last_fail_at` bigint COMMENT '最近业务失败 epoch ms',
  `consec_fail` bigint NOT NULL DEFAULT 0 COMMENT '连续业务失败次数',
  `update_time` datetime COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_metrics_meta_asset_type` (`asset_type`),
  UNIQUE KEY `uk_flow_metrics_meta_asset_type_asset_id` (`asset_type`, `asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量元数据';
