-- Table: flow_asset_version
-- 资产发布历史版本
CREATE TABLE IF NOT EXISTS `flow_asset_version` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `biz_type` varchar(16) NOT NULL COMMENT '资产类型：api / task / service',
  `asset_id` varchar(32) NOT NULL COMMENT '资产ID',
  `version_no` int NOT NULL COMMENT '同资产内递增版本号',
  `snapshot` mediumtext NOT NULL COMMENT '发布快照 JSON（与各模块 published_snapshot 同构）',
  `source` varchar(16) NOT NULL DEFAULT 'publish' COMMENT '来源：publish / rollback',
  `remark` varchar(255) COMMENT '备注',
  `publisher` varchar(64) COMMENT '发布人',
  `publish_time` datetime NOT NULL COMMENT '发布时间',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_asset_version_biz_type_asset_id_publish_time` (`biz_type`, `asset_id`, `publish_time`),
  UNIQUE KEY `uk_flow_asset_version_biz_type_asset_id_version_no` (`biz_type`, `asset_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产发布历史版本';
