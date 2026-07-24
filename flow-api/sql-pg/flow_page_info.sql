-- Table: flow_page_info
-- 页面信息表
CREATE TABLE IF NOT EXISTS flow_page_info (
  id varchar(64) NOT NULL,
  directory_id varchar(64),
  name varchar(256) NOT NULL,
  route_path varchar(512) NOT NULL,
  json text,
  status smallint DEFAULT 0,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_page_info_route_path UNIQUE (route_path)
);
COMMENT ON TABLE flow_page_info IS '页面信息表';
COMMENT ON COLUMN flow_page_info.id IS '主键（雪花ID）';
COMMENT ON COLUMN flow_page_info.directory_id IS '关联全局目录树';
COMMENT ON COLUMN flow_page_info.name IS '页面名称';
COMMENT ON COLUMN flow_page_info.route_path IS '访问路径（唯一）';
COMMENT ON COLUMN flow_page_info.json IS '页面配置 JSON Schema（Amis）';
COMMENT ON COLUMN flow_page_info.status IS '状态：0=草稿，1=已发布';
COMMENT ON COLUMN flow_page_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_page_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_page_info_directory_id ON flow_page_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_page_info_status ON flow_page_info (status);
