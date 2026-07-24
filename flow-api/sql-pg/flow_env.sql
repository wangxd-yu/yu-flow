-- Table: flow_env
-- 发布逻辑环境
CREATE TABLE IF NOT EXISTS flow_env (
  id varchar(64) NOT NULL,
  code varchar(32) NOT NULL,
  name varchar(100) NOT NULL,
  require_suite_pass smallint NOT NULL DEFAULT 0,
  pass_ttl_hours integer NOT NULL DEFAULT 24,
  enabled smallint NOT NULL DEFAULT 1,
  sort_order integer NOT NULL DEFAULT 0,
  remark varchar(500),
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_env_code UNIQUE (code)
);
COMMENT ON TABLE flow_env IS '发布逻辑环境';
COMMENT ON COLUMN flow_env.id IS '主键';
COMMENT ON COLUMN flow_env.code IS 'DEV|STAGING|PROD';
COMMENT ON COLUMN flow_env.name IS '显示名';
COMMENT ON COLUMN flow_env.require_suite_pass IS '1=发布前需回归通过';
COMMENT ON COLUMN flow_env.pass_ttl_hours IS '通过结果有效小时数';
