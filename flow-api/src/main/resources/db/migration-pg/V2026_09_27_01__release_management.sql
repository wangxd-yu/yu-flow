-- 2026-09-27 增量：版本管理与跨环境发布
-- 环境变量、版本单及明细、发布包导入记录；相关权限与「运维实施」角色；数据源密码列加宽（AES-GCM 密文）
-- 均为 IF NOT EXISTS / 幂等写入，可重复执行

-- Table: flow_sys_env_variable
-- 环境变量（每个环境各自维护，值不随发布包迁移）
CREATE TABLE IF NOT EXISTS flow_sys_env_variable (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  var_value varchar(4000),
  secret boolean NOT NULL DEFAULT false,
  remark varchar(512),
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64),
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_env_variable_code UNIQUE (code)
);
COMMENT ON TABLE flow_sys_env_variable IS '环境变量';
COMMENT ON COLUMN flow_sys_env_variable.id IS '雪花ID';
COMMENT ON COLUMN flow_sys_env_variable.code IS '变量名（大写字母开头，仅大写字母/数字/下划线），编排中以 $.env.CODE / ${env.CODE} 引用';
COMMENT ON COLUMN flow_sys_env_variable.var_value IS '变量值；secret=true 时为 AES 密文';
COMMENT ON COLUMN flow_sys_env_variable.secret IS '敏感变量（页面掩码显示，执行轨迹与三方日志脱敏）';
COMMENT ON COLUMN flow_sys_env_variable.remark IS '说明（随发布包导出，提示目标环境该填什么）';
COMMENT ON COLUMN flow_sys_env_variable.create_by IS '创建人';
COMMENT ON COLUMN flow_sys_env_variable.create_time IS '创建时间';
COMMENT ON COLUMN flow_sys_env_variable.update_by IS '更新人';
COMMENT ON COLUMN flow_sys_env_variable.update_time IS '更新时间';

-- Table: flow_release
-- 版本单：一轮上线要带到生产的资产清单
CREATE TABLE IF NOT EXISTS flow_release (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(128),
  status varchar(16) NOT NULL,
  remark text,
  source_env varchar(32),
  frozen_by varchar(64),
  frozen_time timestamp,
  exported_by varchar(64),
  exported_time timestamp,
  package_digest varchar(64),
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64),
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_release_code UNIQUE (code)
);
COMMENT ON TABLE flow_release IS '版本单';
COMMENT ON COLUMN flow_release.id IS '雪花ID';
COMMENT ON COLUMN flow_release.code IS '版本号（全局唯一，如 v2026.10）';
COMMENT ON COLUMN flow_release.name IS '版本名称';
COMMENT ON COLUMN flow_release.status IS '状态：DRAFT 编辑中 / FROZEN 已冻结 / EXPORTED 已导出';
COMMENT ON COLUMN flow_release.remark IS '发布说明（导出时写入包内 CHANGELOG.md）';
COMMENT ON COLUMN flow_release.source_env IS '创建时的实例环境（flow_env.code）';
COMMENT ON COLUMN flow_release.frozen_by IS '冻结人';
COMMENT ON COLUMN flow_release.frozen_time IS '冻结时间';
COMMENT ON COLUMN flow_release.exported_by IS '最近导出人';
COMMENT ON COLUMN flow_release.exported_time IS '最近导出时间';
COMMENT ON COLUMN flow_release.package_digest IS '最近导出包的 manifest SHA-256，用于与生产导入记录对账';
COMMENT ON COLUMN flow_release.create_by IS '创建人';
COMMENT ON COLUMN flow_release.create_time IS '创建时间';
COMMENT ON COLUMN flow_release.update_by IS '更新人';
COMMENT ON COLUMN flow_release.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_release_status ON flow_release (status);
CREATE INDEX IF NOT EXISTS idx_flow_release_create_time ON flow_release (create_time);

-- Table: flow_release_item
-- 版本单明细
CREATE TABLE IF NOT EXISTS flow_release_item (
  id varchar(32) NOT NULL,
  release_id varchar(32) NOT NULL,
  asset_type varchar(32) NOT NULL,
  asset_id varchar(64) NOT NULL,
  asset_name varchar(255),
  asset_key varchar(128),
  action varchar(16) NOT NULL,
  origin varchar(16) NOT NULL,
  content_hash varchar(64),
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_release_item_release_id_atype_aid UNIQUE (release_id, asset_type, asset_id)
);
COMMENT ON TABLE flow_release_item IS '版本单明细';
COMMENT ON COLUMN flow_release_item.id IS '雪花ID';
COMMENT ON COLUMN flow_release_item.release_id IS '版本单ID（flow_release.id）';
COMMENT ON COLUMN flow_release_item.asset_type IS '资产类型：API / SERVICE / TASK / MQ_TASK / RESPONSE_TEMPLATE / PAGE / MODEL / SYS_MACRO / SYS_CONFIG / OPEN_PLATFORM / ALERT_RULE';
COMMENT ON COLUMN flow_release_item.asset_id IS '资产ID';
COMMENT ON COLUMN flow_release_item.asset_name IS '资产名称（冗余，资产删除后仍可显示）';
COMMENT ON COLUMN flow_release_item.asset_key IS '按编码匹配的类型（全局宏/系统配置/开放平台）在目标环境的匹配键';
COMMENT ON COLUMN flow_release_item.action IS '动作：UPSERT 新增或更新 / OFFLINE 下线';
COMMENT ON COLUMN flow_release_item.origin IS '来源：MANUAL 手工加入 / DEPENDENCY 依赖补齐 / SCAN 变更扫描';
COMMENT ON COLUMN flow_release_item.content_hash IS '冻结时的内容指纹（SHA-256），冻结后内容变化即视为漂移';
COMMENT ON COLUMN flow_release_item.create_by IS '创建人';
COMMENT ON COLUMN flow_release_item.create_time IS '创建时间';

-- Table: flow_log_release_import
-- 发布包导入记录（含导入前备份，用于一键回滚）
CREATE TABLE IF NOT EXISTS flow_log_release_import (
  id varchar(32) NOT NULL,
  release_code varchar(64),
  release_name varchar(128),
  package_digest varchar(64),
  source_env varchar(32),
  target_env varchar(32),
  status varchar(16) NOT NULL,
  summary varchar(512),
  error_message varchar(2000),
  report_json text,
  backup_json text,
  asset_hashes text,
  runtime_issues text,
  imported_by varchar(64),
  imported_time timestamp,
  rolled_back_by varchar(64),
  rolled_back_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_release_import IS '发布包导入记录';
COMMENT ON COLUMN flow_log_release_import.id IS '雪花ID';
COMMENT ON COLUMN flow_log_release_import.release_code IS '版本号';
COMMENT ON COLUMN flow_log_release_import.release_name IS '版本名称';
COMMENT ON COLUMN flow_log_release_import.package_digest IS '发布包 manifest SHA-256，可与来源环境版本单对账';
COMMENT ON COLUMN flow_log_release_import.source_env IS '来源环境';
COMMENT ON COLUMN flow_log_release_import.target_env IS '目标环境（本实例）';
COMMENT ON COLUMN flow_log_release_import.status IS '状态：SUCCESS / FAILED / ROLLED_BACK';
COMMENT ON COLUMN flow_log_release_import.summary IS '摘要';
COMMENT ON COLUMN flow_log_release_import.error_message IS '失败原因';
COMMENT ON COLUMN flow_log_release_import.report_json IS '导入报告 JSON';
COMMENT ON COLUMN flow_log_release_import.backup_json IS '导入前受影响资产的完整状态 JSON（回滚依据）';
COMMENT ON COLUMN flow_log_release_import.asset_hashes IS '导入后各资产内容指纹 JSON（类型:ID → 指纹），用于发现生产被直接修改';
COMMENT ON COLUMN flow_log_release_import.runtime_issues IS '导入提交后的运行时自检问题（JSON 字符串数组），为空表示自检通过';
COMMENT ON COLUMN flow_log_release_import.imported_by IS '导入人';
COMMENT ON COLUMN flow_log_release_import.imported_time IS '导入时间';
COMMENT ON COLUMN flow_log_release_import.rolled_back_by IS '回滚人';
COMMENT ON COLUMN flow_log_release_import.rolled_back_time IS '回滚时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_release_import_imported_time ON flow_log_release_import (imported_time);
CREATE INDEX IF NOT EXISTS idx_flow_log_release_import_package_digest ON flow_log_release_import (package_digest);

-- 早期版本已建表时补列
ALTER TABLE flow_log_release_import ADD COLUMN IF NOT EXISTS runtime_issues text;

-- 数据源密码改为 AES-GCM 密文（含随机 IV 与校验标签），比旧 ECB 密文长
ALTER TABLE flow_db_connection ALTER COLUMN password TYPE varchar(512);

-- ===== 权限 =====
INSERT INTO flow_sys_permission (id, perm_code, perm_name, group_code, remark, create_time)
VALUES
('p_env_v', 'sys:env:view', '环境变量查看', 'sys', '平台设置 · 环境变量', CURRENT_TIMESTAMP),
('p_env_w', 'sys:env:write', '环境变量管理', 'sys', '平台设置 · 环境变量', CURRENT_TIMESTAMP),
('p_relpkg_v', 'flow:release:pkg:view', '版本单查看', 'ops', '版本发布 · 版本单', CURRENT_TIMESTAMP),
('p_relpkg_w', 'flow:release:pkg:edit', '版本单管理', 'ops', '版本发布 · 维护/冻结/导出发布包', CURRENT_TIMESTAMP),
('p_relimp', 'flow:release:import', '发布包导入', 'ops', '目标环境导入发布包（导入即发布）', CURRENT_TIMESTAMP),
('p_relrb', 'flow:release:rollback', '导入回滚', 'ops', '回滚最近一次发布包导入', CURRENT_TIMESTAMP),
('p_conn_w', 'flow:conn:write', '连接配置维护', 'infra', 'MQ 连接、OSS 连接、告警通道的新增与修改（不含 MQ 任务、存储场景、告警规则）', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET perm_name = EXCLUDED.perm_name;

-- ===== 运维实施角色 =====
INSERT INTO flow_sys_role (id, role_code, role_name, status, is_builtin, remark, create_time, update_time)
VALUES ('role_deployer', 'DEPLOYER', '运维实施', 1, 1, '目标环境导入发布包、回滚、维护环境变量、数据源与连接配置；不能直接修改编排资产', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET role_name = EXCLUDED.role_name;

-- ===== 角色授权 =====
INSERT INTO flow_sys_role_permission (role_id, perm_code)
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
ON CONFLICT (role_id, perm_code) DO NOTHING;