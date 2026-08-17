-- Table: flow_model_info
-- 数据模型信息表
CREATE TABLE IF NOT EXISTS flow_model_info (
  id varchar(32) NOT NULL,
  directory_id varchar(32),
  name varchar(256) NOT NULL,
  table_name varchar(256) NOT NULL,
  fields_schema text,
  status smallint DEFAULT 0,
  datasource varchar(50),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_model_info_table_name UNIQUE (table_name)
);
COMMENT ON TABLE flow_model_info IS '数据模型信息表';
COMMENT ON COLUMN flow_model_info.id IS '主键（雪花ID）';
COMMENT ON COLUMN flow_model_info.directory_id IS '关联全局目录树';
COMMENT ON COLUMN flow_model_info.name IS '模型中文名，如：用户信息';
COMMENT ON COLUMN flow_model_info.table_name IS '底层物理表名，如：t_user';
COMMENT ON COLUMN flow_model_info.fields_schema IS '核心元数据 JSON 数组（包含字段名、类型、UI配置等）';
COMMENT ON COLUMN flow_model_info.status IS '状态：0=停用，1=启用';
COMMENT ON COLUMN flow_model_info.datasource IS '关联的动态数据源 code';
COMMENT ON COLUMN flow_model_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_model_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_model_info_directory_id ON flow_model_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_model_info_status ON flow_model_info (status);
