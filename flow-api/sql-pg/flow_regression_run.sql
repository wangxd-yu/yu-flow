-- Table: flow_regression_run
-- 回归运行记录
CREATE TABLE IF NOT EXISTS flow_regression_run (
  id varchar(64) NOT NULL,
  suite_id varchar(64) NOT NULL,
  asset_type varchar(32) NOT NULL,
  asset_id varchar(64) NOT NULL,
  env_code varchar(32) NOT NULL,
  status varchar(20) NOT NULL,
  total_cases integer NOT NULL DEFAULT 0,
  passed_cases integer NOT NULL DEFAULT 0,
  failed_cases integer NOT NULL DEFAULT 0,
  started_at timestamp NOT NULL,
  finished_at timestamp,
  triggered_by varchar(100),
  summary varchar(500),
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_run IS '回归运行记录';
COMMENT ON COLUMN flow_regression_run.status IS 'RUNNING|PASSED|FAILED|ERROR';
CREATE INDEX IF NOT EXISTS idx_flow_regression_run_asset_type_asset_id_env_code_finished_at ON flow_regression_run (asset_type, asset_id, env_code, finished_at);
CREATE INDEX IF NOT EXISTS idx_flow_regression_run_suite_id ON flow_regression_run (suite_id);
