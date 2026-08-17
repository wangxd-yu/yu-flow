-- Table: flow_sys_config
-- 系统配置表 (System Configuration)
CREATE TABLE IF NOT EXISTS flow_sys_config (
  id varchar(32) NOT NULL,
  config_key varchar(100) NOT NULL,
  config_value text,
  value_type varchar(20) NOT NULL DEFAULT 'STRING',
  config_group varchar(50) NOT NULL DEFAULT 'GENERAL',
  remark varchar(500),
  is_builtin smallint NOT NULL DEFAULT 0,
  status smallint NOT NULL DEFAULT 1,
  sort_order integer NOT NULL DEFAULT 100,
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64),
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_config_config_key UNIQUE (config_key)
);
COMMENT ON TABLE flow_sys_config IS '系统配置表 (System Configuration)';
COMMENT ON COLUMN flow_sys_config.id IS '雪花ID';
COMMENT ON COLUMN flow_sys_config.config_key IS '配置键 (唯一标识，如: SYSTEM_PREFIX, TOKEN_EXPIRE)';
COMMENT ON COLUMN flow_sys_config.config_value IS '配置值 (支持字符串、数字、JSON 等格式)';
COMMENT ON COLUMN flow_sys_config.value_type IS '值类型 (STRING / NUMBER / BOOLEAN / JSON)';
COMMENT ON COLUMN flow_sys_config.config_group IS '配置分组 (GENERAL / SECURITY / OSS / GATEWAY 等)';
COMMENT ON COLUMN flow_sys_config.remark IS '配置说明';
COMMENT ON COLUMN flow_sys_config.is_builtin IS '是否内置 (1: 内置参数, 不允许删除; 0: 用户自定义)';
COMMENT ON COLUMN flow_sys_config.status IS '状态 (1: 启用, 0: 停用)';
COMMENT ON COLUMN flow_sys_config.sort_order IS '组内展示顺序，越小越靠前';
COMMENT ON COLUMN flow_sys_config.create_by IS '创建者';
COMMENT ON COLUMN flow_sys_config.create_time IS '创建时间';
COMMENT ON COLUMN flow_sys_config.update_by IS '更新者';
COMMENT ON COLUMN flow_sys_config.update_time IS '更新时间';
