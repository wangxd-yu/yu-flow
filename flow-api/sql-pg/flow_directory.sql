-- Table: flow_directory
-- 全局目录表
CREATE TABLE IF NOT EXISTS flow_directory (
  id varchar(64) NOT NULL,
  parent_id varchar(64),
  name varchar(128) NOT NULL,
  biz_type varchar(32),
  sort integer DEFAULT 0,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_directory IS '全局目录表';
COMMENT ON COLUMN flow_directory.id IS '主键（雪花ID）';
COMMENT ON COLUMN flow_directory.parent_id IS '父节点ID，NULL 表示根节点';
COMMENT ON COLUMN flow_directory.name IS '目录名称';
COMMENT ON COLUMN flow_directory.biz_type IS '业务域：api/task/service/model/page，空=共用';
COMMENT ON COLUMN flow_directory.sort IS '排序（升序）';
COMMENT ON COLUMN flow_directory.create_time IS '创建时间';
COMMENT ON COLUMN flow_directory.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_directory_biz_type ON flow_directory (biz_type);
CREATE INDEX IF NOT EXISTS idx_flow_directory_parent_id ON flow_directory (parent_id);
