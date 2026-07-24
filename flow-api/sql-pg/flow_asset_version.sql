-- Table: flow_asset_version
-- 资产发布历史版本
CREATE TABLE IF NOT EXISTS flow_asset_version (
  id varchar(32) NOT NULL,
  biz_type varchar(16) NOT NULL,
  asset_id varchar(32) NOT NULL,
  version_no integer NOT NULL,
  snapshot text NOT NULL,
  source varchar(16) NOT NULL DEFAULT 'publish',
  remark varchar(255),
  publisher varchar(64),
  publish_time timestamp NOT NULL,
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_asset_version_biz_type_asset_id_version_no UNIQUE (biz_type, asset_id, version_no)
);
COMMENT ON TABLE flow_asset_version IS '资产发布历史版本';
COMMENT ON COLUMN flow_asset_version.id IS '雪花ID';
COMMENT ON COLUMN flow_asset_version.biz_type IS '资产类型：api / task / service';
COMMENT ON COLUMN flow_asset_version.asset_id IS '资产ID';
COMMENT ON COLUMN flow_asset_version.version_no IS '同资产内递增版本号';
COMMENT ON COLUMN flow_asset_version.snapshot IS '发布快照 JSON（与各模块 published_snapshot 同构）';
COMMENT ON COLUMN flow_asset_version.source IS '来源：publish / rollback';
COMMENT ON COLUMN flow_asset_version.remark IS '备注';
COMMENT ON COLUMN flow_asset_version.publisher IS '发布人';
COMMENT ON COLUMN flow_asset_version.publish_time IS '发布时间';
COMMENT ON COLUMN flow_asset_version.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_asset_version_biz_type_asset_id_publish_time ON flow_asset_version (biz_type, asset_id, publish_time);
