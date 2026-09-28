-- 2026-09-27 增量：版本管理与跨环境发布
-- 环境变量、版本单及明细、发布包导入记录；相关权限与「运维实施」角色；数据源密码列加宽（AES-GCM 密文）
-- 均为 IF NOT EXISTS / 幂等写入，可重复执行

-- Table: flow_sys_env_variable
-- 环境变量（每个环境各自维护，值不随发布包迁移）
CREATE TABLE IF NOT EXISTS `flow_sys_env_variable` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '变量名（大写字母开头，仅大写字母/数字/下划线），编排中以 $.env.CODE / ${env.CODE} 引用',
  `var_value` varchar(4000) COMMENT '变量值；secret=1 时为 AES 密文',
  `secret` tinyint(1) NOT NULL DEFAULT 0 COMMENT '敏感变量：0=否, 1=是（页面掩码显示，执行轨迹与三方日志脱敏）',
  `remark` varchar(512) COMMENT '说明（随发布包导出，提示目标环境该填什么）',
  `create_by` varchar(64) COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COMMENT '更新人',
  `update_time` datetime COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_sys_env_variable_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境变量';

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

-- 早期版本已建表时补列
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_log_release_import'
    AND COLUMN_NAME = 'runtime_issues');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `flow_log_release_import` ADD COLUMN `runtime_issues` text COMMENT ''导入提交后的运行时自检问题（JSON 字符串数组），为空表示自检通过'' AFTER `asset_hashes`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 数据源密码改为 AES-GCM 密文（含随机 IV 与校验标签），比旧 ECB 密文长
ALTER TABLE `flow_db_connection` MODIFY COLUMN `password` varchar(512) NOT NULL COMMENT '密码（AES 密文）';

-- ===== 权限 =====
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
('p_env_v', 'sys:env:view', '环境变量查看', 'sys', '平台设置 · 环境变量', NOW()),
('p_env_w', 'sys:env:write', '环境变量管理', 'sys', '平台设置 · 环境变量', NOW()),
('p_relpkg_v', 'flow:release:pkg:view', '版本单查看', 'ops', '版本发布 · 版本单', NOW()),
('p_relpkg_w', 'flow:release:pkg:edit', '版本单管理', 'ops', '版本发布 · 维护/冻结/导出发布包', NOW()),
('p_relimp', 'flow:release:import', '发布包导入', 'ops', '目标环境导入发布包（导入即发布）', NOW()),
('p_relrb', 'flow:release:rollback', '导入回滚', 'ops', '回滚最近一次发布包导入', NOW()),
('p_conn_w', 'flow:conn:write', '连接配置维护', 'infra', 'MQ 连接、OSS 连接、告警通道的新增与修改（不含 MQ 任务、存储场景、告警规则）', NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- ===== 运维实施角色 =====
INSERT INTO `flow_sys_role` (`id`, `role_code`, `role_name`, `status`, `is_builtin`, `remark`, `create_time`, `update_time`)
VALUES ('role_deployer', 'DEPLOYER', '运维实施', 1, 1, '目标环境导入发布包、回滚、维护环境变量、数据源与连接配置；不能直接修改编排资产', NOW(), NOW())
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- ===== 角色授权 =====
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
('role_operator', 'sys:env:view'),
('role_operator', 'flow:release:pkg:view'),
('role_operator', 'flow:release:pkg:edit'),
('role_viewer', 'sys:env:view'),
('role_viewer', 'flow:release:pkg:view'),
('role_deployer', 'home:view'),
('role_deployer', 'flow:release:pkg:view'),
('role_deployer', 'flow:release:import'),
('role_deployer', 'flow:release:rollback'),
('role_deployer', 'sys:env:view'),
('role_deployer', 'sys:env:write'),
('role_deployer', 'flow:ds:view'),
('role_deployer', 'flow:ds:write'),
('role_deployer', 'flow:mq:view'),
('role_deployer', 'flow:oss:view'),
('role_deployer', 'flow:alert:view'),
('role_deployer', 'flow:conn:write'),
('role_deployer', 'flow:api:view'),
('role_deployer', 'flow:service:view'),
('role_deployer', 'flow:task:view'),
('role_deployer', 'flow:runtime:view'),
('role_deployer', 'log:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);