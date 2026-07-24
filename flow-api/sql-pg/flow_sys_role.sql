-- Table: flow_sys_role
-- 系统角色
CREATE TABLE IF NOT EXISTS flow_sys_role (
  id varchar(32) NOT NULL,
  role_code varchar(64) NOT NULL,
  role_name varchar(64) NOT NULL,
  status smallint NOT NULL DEFAULT 1,
  is_builtin smallint NOT NULL DEFAULT 0,
  remark varchar(255),
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_role_role_code UNIQUE (role_code)
);
COMMENT ON TABLE flow_sys_role IS '系统角色';
COMMENT ON COLUMN flow_sys_role.role_code IS 'ADMIN/OPERATOR/VIEWER';
