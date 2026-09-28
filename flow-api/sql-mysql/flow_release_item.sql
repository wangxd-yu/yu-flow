-- Table: flow_release_item
-- 版本单明细
CREATE TABLE IF NOT EXISTS `flow_release_item` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `release_id` varchar(32) NOT NULL COMMENT '版本单ID（flow_release.id）',
  `asset_type` varchar(32) NOT NULL COMMENT '资产类型：API / SERVICE / TASK / MQ_TASK / RESPONSE_TEMPLATE / PAGE / MODEL / SYS_MACRO / SYS_CONFIG / OPEN_PLATFORM / ALERT_RULE',
  `asset_id` varchar(64) NOT NULL COMMENT '资产ID',
  `asset_name` varchar(255) COMMENT '资产名称（冗余，资产删除后仍可显示）',
  `asset_key` varchar(128) COMMENT '按编码匹配的类型（全局宏/系统配置/开放平台）在目标环境的匹配键',
  `action` varchar(16) NOT NULL COMMENT '动作：UPSERT 新增或更新 / OFFLINE 下线',
  `origin` varchar(16) NOT NULL COMMENT '来源：MANUAL 手工加入 / DEPENDENCY 依赖补齐 / SCAN 变更扫描',
  `content_hash` varchar(64) COMMENT '冻结时的内容指纹（SHA-256），冻结后内容变化即视为漂移',
  `create_by` varchar(64) COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_release_item_release_id_atype_aid` (`release_id`, `asset_type`, `asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本单明细';
