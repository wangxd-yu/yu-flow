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
