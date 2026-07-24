-- Table: flow_sys_user
-- 系统用户
CREATE TABLE IF NOT EXISTS flow_sys_user (
  id varchar(32) NOT NULL,
  username varchar(64) NOT NULL,
  password_hash varchar(128) NOT NULL,
  display_name varchar(64),
  status smallint NOT NULL DEFAULT 1,
  is_builtin smallint NOT NULL DEFAULT 0,
  remark varchar(255),
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_user_username UNIQUE (username)
);
COMMENT ON TABLE flow_sys_user IS '系统用户';
COMMENT ON COLUMN flow_sys_user.username IS '登录名';
COMMENT ON COLUMN flow_sys_user.password_hash IS 'BCrypt 密码';
COMMENT ON COLUMN flow_sys_user.display_name IS '显示名';
COMMENT ON COLUMN flow_sys_user.status IS '1启用 0停用';
COMMENT ON COLUMN flow_sys_user.is_builtin IS '1内置不可删';
