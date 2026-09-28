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
