-- 资产发布历史版本（接口 / 任务 / 服务编排共用）
CREATE TABLE IF NOT EXISTS `flow_asset_version` (
    `id`            VARCHAR(32)   NOT NULL COMMENT '雪花ID',
    `biz_type`      VARCHAR(16)   NOT NULL COMMENT '资产类型：api / task / service',
    `asset_id`      VARCHAR(32)   NOT NULL COMMENT '资产ID',
    `version_no`    INT           NOT NULL COMMENT '同资产内递增版本号',
    `snapshot`      MEDIUMTEXT    NOT NULL COMMENT '发布快照 JSON（与各模块 published_snapshot 同构）',
    `source`        VARCHAR(16)   NOT NULL DEFAULT 'publish' COMMENT '来源：publish / rollback',
    `remark`        VARCHAR(255)           COMMENT '备注',
    `publisher`     VARCHAR(64)            COMMENT '发布人',
    `publish_time`  DATETIME      NOT NULL COMMENT '发布时间',
    `create_time`   DATETIME               COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_asset_ver` (`biz_type`, `asset_id`, `version_no`),
    KEY `idx_biz_asset_time` (`biz_type`, `asset_id`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产发布历史版本';
