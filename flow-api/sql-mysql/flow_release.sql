-- Table: flow_release
-- 版本单：一轮上线要带到生产的资产清单
CREATE TABLE IF NOT EXISTS `flow_release` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '版本号（全局唯一，如 v2026.10）',
  `name` varchar(128) COMMENT '版本名称',
  `status` varchar(16) NOT NULL COMMENT '状态：DRAFT 编辑中 / FROZEN 已冻结 / EXPORTED 已导出',
  `remark` text COMMENT '发布说明（导出时写入包内 CHANGELOG.md）',
  `source_env` varchar(32) COMMENT '创建时的实例环境（flow_env.code）',
  `frozen_by` varchar(64) COMMENT '冻结人',
  `frozen_time` datetime COMMENT '冻结时间',
  `exported_by` varchar(64) COMMENT '最近导出人',
  `exported_time` datetime COMMENT '最近导出时间',
  `package_digest` varchar(64) COMMENT '最近导出包的 manifest SHA-256，用于与生产导入记录对账',
  `create_by` varchar(64) COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新人',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_release_code` (`code`),
  KEY `idx_flow_release_status` (`status`),
  KEY `idx_flow_release_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本单';
