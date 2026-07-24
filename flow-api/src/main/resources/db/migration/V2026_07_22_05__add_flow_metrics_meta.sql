-- 资产运行计量：跨重启持久化的健康元数据（last*/consecFail）
CREATE TABLE IF NOT EXISTS `flow_metrics_meta` (
    `id`               VARCHAR(32)  NOT NULL COMMENT '雪花ID',
    `asset_type`       VARCHAR(16)  NOT NULL COMMENT 'API / TASK / SERVICE / PLATFORM',
    `asset_id`         VARCHAR(32)  NOT NULL COMMENT '资产ID',
    `last_success_at`  BIGINT                COMMENT '最近成功 epoch ms',
    `last_fail_at`     BIGINT                COMMENT '最近业务失败 epoch ms',
    `consec_fail`      BIGINT       NOT NULL DEFAULT 0 COMMENT '连续业务失败次数',
    `update_time`      DATETIME              COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_metrics_meta_asset` (`asset_type`, `asset_id`),
    KEY `idx_metrics_meta_type` (`asset_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产运行计量元数据';
