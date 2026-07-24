-- Table: flow_log_login
-- 登录日志表
CREATE TABLE IF NOT EXISTS flow_log_login (
  id varchar(64) NOT NULL,
  account varchar(100) NOT NULL,
  ip varchar(64),
  region varchar(255),
  user_agent varchar(512),
  status smallint NOT NULL DEFAULT 0,
  msg varchar(255),
  duration bigint,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_log_login IS '登录日志表';
COMMENT ON COLUMN flow_log_login.id IS '主键';
COMMENT ON COLUMN flow_log_login.account IS '登录账号';
COMMENT ON COLUMN flow_log_login.ip IS '客户端 IP';
COMMENT ON COLUMN flow_log_login.region IS 'IP 归属地区';
COMMENT ON COLUMN flow_log_login.user_agent IS '浏览器 User-Agent';
COMMENT ON COLUMN flow_log_login.status IS '登录状态（1: 成功, 0: 失败）';
COMMENT ON COLUMN flow_log_login.msg IS '登录结果信息';
COMMENT ON COLUMN flow_log_login.duration IS '登录耗时（毫秒）';
COMMENT ON COLUMN flow_log_login.create_time IS '登录时间';
CREATE INDEX IF NOT EXISTS idx_flow_log_login_account ON flow_log_login (account);
CREATE INDEX IF NOT EXISTS idx_flow_log_login_create_time ON flow_log_login (create_time);
