-- Table: flow_open_platform
-- 第三方开放平台
CREATE TABLE IF NOT EXISTS flow_open_platform (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  status smallint NOT NULL DEFAULT 1,
  contact varchar(128),
  remark varchar(512),
  ip_allowlist varchar(1024),
  expire_at timestamp,
  open_call_log_enabled smallint DEFAULT 1,
  rate_limit_qps integer,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_open_platform_code UNIQUE (code)
);
COMMENT ON TABLE flow_open_platform IS '第三方开放平台';
COMMENT ON COLUMN flow_open_platform.id IS '雪花ID';
COMMENT ON COLUMN flow_open_platform.code IS '唯一编码';
COMMENT ON COLUMN flow_open_platform.name IS '平台名称';
COMMENT ON COLUMN flow_open_platform.status IS '0停用 1启用';
COMMENT ON COLUMN flow_open_platform.contact IS '联系人';
COMMENT ON COLUMN flow_open_platform.remark IS '备注';
COMMENT ON COLUMN flow_open_platform.ip_allowlist IS 'IP白名单JSON数组，空=不限';
COMMENT ON COLUMN flow_open_platform.expire_at IS '平台到期时间';
COMMENT ON COLUMN flow_open_platform.open_call_log_enabled IS '是否记录入站摘要日志 0关1开';
COMMENT ON COLUMN flow_open_platform.rate_limit_qps IS '平台级 QPS 上限，空=不限';
CREATE INDEX IF NOT EXISTS idx_flow_open_platform_status ON flow_open_platform (status);
