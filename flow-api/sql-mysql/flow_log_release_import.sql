-- Table: flow_log_release_import
-- 发布包导入记录（含导入前备份，用于一键回滚）
CREATE TABLE IF NOT EXISTS `flow_log_release_import` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `release_code` varchar(64) COMMENT '版本号',
  `release_name` varchar(128) COMMENT '版本名称',
  `package_digest` varchar(64) COMMENT '发布包 manifest SHA-256，可与来源环境版本单对账',
  `source_env` varchar(32) COMMENT '来源环境',
  `target_env` varchar(32) COMMENT '目标环境（本实例）',
  `status` varchar(16) NOT NULL COMMENT '状态：SUCCESS / FAILED / ROLLED_BACK',
  `summary` varchar(512) COMMENT '摘要',
  `error_message` varchar(2000) COMMENT '失败原因',
  `report_json` mediumtext COMMENT '导入报告 JSON',
  `backup_json` longtext COMMENT '导入前受影响资产的完整状态 JSON（回滚依据）',
  `asset_hashes` mediumtext COMMENT '导入后各资产内容指纹 JSON（类型:ID → 指纹），用于发现生产被直接修改',
  `runtime_issues` text COMMENT '导入提交后的运行时自检问题（JSON 字符串数组），为空表示自检通过',
  `imported_by` varchar(64) COMMENT '导入人',
  `imported_time` datetime COMMENT '导入时间',
  `rolled_back_by` varchar(64) COMMENT '回滚人',
  `rolled_back_time` datetime COMMENT '回滚时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_release_import_imported_time` (`imported_time`),
  KEY `idx_flow_log_release_import_package_digest` (`package_digest`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布包导入记录';
