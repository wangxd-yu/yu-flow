-- Table: flow_sys_permission
-- 权限点
CREATE TABLE IF NOT EXISTS flow_sys_permission (
  id varchar(32) NOT NULL,
  perm_code varchar(128) NOT NULL,
  perm_name varchar(64) NOT NULL,
  group_code varchar(64),
  remark varchar(255),
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_permission_perm_code UNIQUE (perm_code)
);
COMMENT ON TABLE flow_sys_permission IS '权限点';
COMMENT ON COLUMN flow_sys_permission.perm_code IS '如 flow:api:write';
COMMENT ON COLUMN flow_sys_permission.group_code IS '分组：flow/ops/infra/sys';
