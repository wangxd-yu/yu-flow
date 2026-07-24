-- Table: flow_sys_user_role
-- 用户-角色
CREATE TABLE IF NOT EXISTS flow_sys_user_role (
  user_id varchar(32) NOT NULL,
  role_id varchar(32) NOT NULL,
  PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE flow_sys_user_role IS '用户-角色';
CREATE INDEX IF NOT EXISTS idx_flow_sys_user_role_role_id ON flow_sys_user_role (role_id);
