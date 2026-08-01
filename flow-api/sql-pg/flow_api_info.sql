-- Table: flow_api_info
-- 接口配置类
CREATE TABLE IF NOT EXISTS flow_api_info (
  id bigint NOT NULL,
  name varchar(20),
  directory_id varchar(64),
  url varchar(100),
  datasource varchar(20),
  module varchar(20),
  method varchar(10),
  service_type varchar(20),
  intercept_mode varchar(16) NOT NULL DEFAULT 'REPLACE',
  host_binding text,
  response_type varchar(10),
  version varchar(20),
  config text,
  publish_status smallint,
  contract text,
  tags varchar(500),
  level integer,
  template_id varchar(32),
  custom_success_wrapper text,
  custom_page_wrapper text,
  custom_fail_wrapper text,
  info text,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp,
  deleted integer DEFAULT 0,
  dsl_content text,
  sql_content text,
  json_content text,
  text_content text,
  published_snapshot text,
  publish_time timestamp,
  log_enabled smallint NOT NULL DEFAULT 1,
  log_retention_days integer,
  cache_config text,
  security_config text,
  view_export_config text,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_api_info IS '接口配置类';
COMMENT ON COLUMN flow_api_info.id IS '主键ID，通过Snowflake算法生成';
COMMENT ON COLUMN flow_api_info.name IS 'API配置的名称';
COMMENT ON COLUMN flow_api_info.directory_id IS '关联全局目录树';
COMMENT ON COLUMN flow_api_info.url IS 'API的URL路径';
COMMENT ON COLUMN flow_api_info.datasource IS '数据源名称';
COMMENT ON COLUMN flow_api_info.module IS '所属模块名称';
COMMENT ON COLUMN flow_api_info.method IS '请求方式：POST、PUT、GET、DELETE';
COMMENT ON COLUMN flow_api_info.service_type IS '服务驱动类型：DB、FLOW、JSON、STRING、HOST';
COMMENT ON COLUMN flow_api_info.intercept_mode IS '同名拦截：REPLACE-替换执行引擎；WRAP-包裹转发宿主';
COMMENT ON COLUMN flow_api_info.host_binding IS 'WRAP 宿主绑定 JSON：forward/targetPath/probePath';
COMMENT ON COLUMN flow_api_info.response_type IS '响应数据类型：PAGE(分页)、LIST(列表)、OBJECT(对象)';
COMMENT ON COLUMN flow_api_info.version IS 'API版本号';
COMMENT ON COLUMN flow_api_info.config IS '核心逻辑配置，存储SQL、流编排JSON或静态数据';
COMMENT ON COLUMN flow_api_info.publish_status IS '发布状态：0：未发布；1：已发布';
COMMENT ON COLUMN flow_api_info.tags IS '标签，英文都好分隔';
COMMENT ON COLUMN flow_api_info.level IS '优先级，与请求的ss-level比较，大的优先';
COMMENT ON COLUMN flow_api_info.template_id IS '基座模板ID';
COMMENT ON COLUMN flow_api_info.custom_success_wrapper IS '自定义成功返回包装';
COMMENT ON COLUMN flow_api_info.custom_page_wrapper IS '自定义分页返回包装';
COMMENT ON COLUMN flow_api_info.custom_fail_wrapper IS '自定义失败返回包装';
COMMENT ON COLUMN flow_api_info.info IS 'API配置的详细描述';
COMMENT ON COLUMN flow_api_info.create_time IS '创建时间，自动记录为当前时间';
COMMENT ON COLUMN flow_api_info.update_time IS '更新时间';
COMMENT ON COLUMN flow_api_info.dsl_content IS '逻辑编排 (FLOW) — Flow DSL JSON';
COMMENT ON COLUMN flow_api_info.sql_content IS '数据库 (DB) — SQL 脚本';
COMMENT ON COLUMN flow_api_info.json_content IS '静态 JSON (JSON) — JSON 内容';
COMMENT ON COLUMN flow_api_info.text_content IS '静态文本 (STRING) — 纯文本内容';
COMMENT ON COLUMN flow_api_info.published_snapshot IS '发布时的完整内容快照 (JSON)，运行时引擎从此字段读取';
COMMENT ON COLUMN flow_api_info.publish_time IS '最近一次发布时间';
COMMENT ON COLUMN flow_api_info.log_enabled IS '是否记录执行日志：1-开启，0-关闭';
COMMENT ON COLUMN flow_api_info.log_retention_days IS '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数';
COMMENT ON COLUMN flow_api_info.cache_config IS '响应缓存配置 JSON：enabled/ttlSeconds/keyParams/includePageable';
COMMENT ON COLUMN flow_api_info.security_config IS '入站防护 JSON：authMode/antiReplay/rateLimit/ipAllowlist';
COMMENT ON COLUMN flow_api_info.view_export_config IS '数据查看与导出 JSON：columns/sheetName/maxExportRows';
CREATE INDEX IF NOT EXISTS idx_flow_api_info_directory_id ON flow_api_info (directory_id);
