-- Table: flow_metrics_meta
-- 资产运行计量元数据
CREATE TABLE IF NOT EXISTS flow_metrics_meta (
  id varchar(32) NOT NULL,
  asset_type varchar(16) NOT NULL,
  asset_id varchar(32) NOT NULL,
  last_success_at bigint,
  last_fail_at bigint,
  consec_fail bigint NOT NULL DEFAULT 0,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_metrics_meta_asset_type_asset_id UNIQUE (asset_type, asset_id)
);
COMMENT ON TABLE flow_metrics_meta IS '资产运行计量元数据';
COMMENT ON COLUMN flow_metrics_meta.id IS '雪花ID';
COMMENT ON COLUMN flow_metrics_meta.asset_type IS 'API / TASK / MQ_TASK / SERVICE / PLATFORM / SYSTEM';
COMMENT ON COLUMN flow_metrics_meta.asset_id IS '资产ID';
COMMENT ON COLUMN flow_metrics_meta.last_success_at IS '最近成功 epoch ms';
COMMENT ON COLUMN flow_metrics_meta.last_fail_at IS '最近业务失败 epoch ms';
COMMENT ON COLUMN flow_metrics_meta.consec_fail IS '连续业务失败次数';
COMMENT ON COLUMN flow_metrics_meta.update_time IS '最后更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_metrics_meta_asset_type ON flow_metrics_meta (asset_type);
