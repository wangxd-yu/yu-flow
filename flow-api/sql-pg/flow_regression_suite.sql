-- Table: flow_regression_suite
-- 回归测试套件
CREATE TABLE IF NOT EXISTS flow_regression_suite (
  id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  asset_type varchar(32) NOT NULL,
  asset_id varchar(64) NOT NULL,
  enabled smallint NOT NULL DEFAULT 1,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_suite IS '回归测试套件';
COMMENT ON COLUMN flow_regression_suite.asset_type IS 'API|TASK|SERVICE';
CREATE INDEX IF NOT EXISTS idx_flow_regression_suite_asset_type_asset_id ON flow_regression_suite (asset_type, asset_id);
