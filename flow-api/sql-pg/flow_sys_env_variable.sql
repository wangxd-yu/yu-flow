-- Table: flow_sys_env_variable
-- 环境变量（每个环境各自维护，值不随发布包迁移）
CREATE TABLE IF NOT EXISTS flow_sys_env_variable (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  var_value varchar(4000),
  secret boolean NOT NULL DEFAULT false,
  remark varchar(512),
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64),
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_env_variable_code UNIQUE (code)
);
COMMENT ON TABLE flow_sys_env_variable IS '环境变量';
COMMENT ON COLUMN flow_sys_env_variable.id IS '雪花ID';
COMMENT ON COLUMN flow_sys_env_variable.code IS '变量名（大写字母开头，仅大写字母/数字/下划线），编排中以 $.env.CODE / ${env.CODE} 引用';
COMMENT ON COLUMN flow_sys_env_variable.var_value IS '变量值；secret=true 时为 AES 密文';
COMMENT ON COLUMN flow_sys_env_variable.secret IS '敏感变量（页面掩码显示，执行轨迹与三方日志脱敏）';
COMMENT ON COLUMN flow_sys_env_variable.remark IS '说明（随发布包导出，提示目标环境该填什么）';
COMMENT ON COLUMN flow_sys_env_variable.create_by IS '创建人';
COMMENT ON COLUMN flow_sys_env_variable.create_time IS '创建时间';
COMMENT ON COLUMN flow_sys_env_variable.update_by IS '更新人';
COMMENT ON COLUMN flow_sys_env_variable.update_time IS '更新时间';
