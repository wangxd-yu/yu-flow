-- Table: flow_api_excel_template
-- API Excel 导出模板
CREATE TABLE IF NOT EXISTS flow_api_excel_template (
  id varchar(64) NOT NULL,
  api_id varchar(64) NOT NULL,
  file_name varchar(255) NOT NULL,
  content_type varchar(120),
  content bytea NOT NULL,
  file_size integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_api_excel_template_api_id UNIQUE (api_id)
);
COMMENT ON TABLE flow_api_excel_template IS 'API Excel 导出模板';
COMMENT ON COLUMN flow_api_excel_template.id IS '主键';
COMMENT ON COLUMN flow_api_excel_template.api_id IS '接口 ID';
COMMENT ON COLUMN flow_api_excel_template.file_name IS '原始文件名';
COMMENT ON COLUMN flow_api_excel_template.content_type IS 'MIME';
COMMENT ON COLUMN flow_api_excel_template.content IS 'xlsx 二进制';
