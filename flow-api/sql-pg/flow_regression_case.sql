-- Table: flow_regression_case
-- 回归用例
CREATE TABLE IF NOT EXISTS flow_regression_case (
  id varchar(64) NOT NULL,
  suite_id varchar(64) NOT NULL,
  name varchar(100) NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  enabled smallint NOT NULL DEFAULT 1,
  headers_json text,
  query_json text,
  body text,
  expect_trace_status varchar(20),
  expect_json_path varchar(128),
  expect_value varchar(500),
  timeout_ms integer NOT NULL DEFAULT 10000,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_regression_case IS '回归用例';
COMMENT ON COLUMN flow_regression_case.headers_json IS '请求头 JSON（禁止敏感头）';
COMMENT ON COLUMN flow_regression_case.query_json IS 'Query JSON';
COMMENT ON COLUMN flow_regression_case.body IS '请求体，≤32KB';
COMMENT ON COLUMN flow_regression_case.expect_trace_status IS 'success|error，空则不校验';
COMMENT ON COLUMN flow_regression_case.expect_json_path IS '简单 JSONPath';
COMMENT ON COLUMN flow_regression_case.expect_value IS '期望值（字符串比较）';
CREATE INDEX IF NOT EXISTS idx_flow_regression_case_suite_id ON flow_regression_case (suite_id);
