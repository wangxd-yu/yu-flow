-- Table: flow_response_template
-- API 响应模板表
CREATE TABLE IF NOT EXISTS flow_response_template (
  id varchar(32) NOT NULL,
  template_name varchar(100) NOT NULL,
  success_wrapper text,
  page_wrapper text,
  fail_wrapper text,
  is_default smallint NOT NULL DEFAULT 0,
  remark varchar(500),
  create_by varchar(64),
  create_time timestamp,
  update_by varchar(64),
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_response_template_template_name UNIQUE (template_name)
);
COMMENT ON TABLE flow_response_template IS 'API 响应模板表';
COMMENT ON COLUMN flow_response_template.id IS '主键 (雪花ID)';
COMMENT ON COLUMN flow_response_template.template_name IS '模板名称';
COMMENT ON COLUMN flow_response_template.success_wrapper IS '成功包装体 JSON 模板';
COMMENT ON COLUMN flow_response_template.page_wrapper IS '分页包装体 JSON 模板';
COMMENT ON COLUMN flow_response_template.fail_wrapper IS '失败包装体 JSON 模板';
COMMENT ON COLUMN flow_response_template.is_default IS '是否全局默认 (1:默认 0:非默认)';
COMMENT ON COLUMN flow_response_template.remark IS '备注说明';
COMMENT ON COLUMN flow_response_template.create_by IS '创建者';
COMMENT ON COLUMN flow_response_template.create_time IS '创建时间';
COMMENT ON COLUMN flow_response_template.update_by IS '更新者';
COMMENT ON COLUMN flow_response_template.update_time IS '更新时间';
