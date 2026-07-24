-- Table: flow_regression_run_case
-- 回归用例运行明细
CREATE TABLE IF NOT EXISTS flow_regression_run_case (
  id varchar(64) NOT NULL,
  run_id varchar(64) NOT NULL,
  case_id varchar(64) NOT NULL,
  case_name varchar(100),
  status varchar(20) NOT NULL,
  duration_ms bigint,
  message varchar(500),
  detail_json varchar(2000),
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_run_case IS '回归用例运行明细';
COMMENT ON COLUMN flow_regression_run_case.status IS 'PASSED|FAILED|ERROR|SKIPPED';
COMMENT ON COLUMN flow_regression_run_case.detail_json IS '截断后的摘要，不含全量响应';
CREATE INDEX IF NOT EXISTS idx_flow_regression_run_case_run_id ON flow_regression_run_case (run_id);
