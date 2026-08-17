-- Table: flow_page_directory
-- 页面目录表
CREATE TABLE IF NOT EXISTS flow_page_directory (
  id varchar(32) NOT NULL,
  parent_id varchar(32),
  name varchar(128) NOT NULL,
  sort integer DEFAULT 0,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_page_directory IS '页面目录表';
COMMENT ON COLUMN flow_page_directory.id IS '主键（雪花ID）';
COMMENT ON COLUMN flow_page_directory.parent_id IS '父节点ID，NULL 表示根节点';
COMMENT ON COLUMN flow_page_directory.name IS '目录名称';
COMMENT ON COLUMN flow_page_directory.sort IS '排序（升序）';
COMMENT ON COLUMN flow_page_directory.create_time IS '创建时间';
COMMENT ON COLUMN flow_page_directory.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_page_directory_parent_id ON flow_page_directory (parent_id);
