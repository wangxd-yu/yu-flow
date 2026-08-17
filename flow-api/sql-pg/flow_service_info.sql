-- Table: flow_service_info
-- 内部服务编排定义
CREATE TABLE IF NOT EXISTS flow_service_info (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  directory_id varchar(32),
  enabled boolean NOT NULL DEFAULT true,
  log_enabled boolean NOT NULL DEFAULT true,
  log_mode varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
  dsl_content text,
  contract text,
  publish_status smallint NOT NULL DEFAULT 0,
  published_snapshot text,
  publish_time timestamp,
  info varchar(512),
  tags varchar(255),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_service_info IS '内部服务编排定义';
COMMENT ON COLUMN flow_service_info.id IS '雪花ID';
COMMENT ON COLUMN flow_service_info.name IS '服务名称';
COMMENT ON COLUMN flow_service_info.directory_id IS '关联目录ID（复用全局目录树）';
COMMENT ON COLUMN flow_service_info.enabled IS '启用状态：0=停用, 1=启用';
COMMENT ON COLUMN flow_service_info.log_enabled IS '是否记录执行日志';
COMMENT ON COLUMN flow_service_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';
COMMENT ON COLUMN flow_service_info.dsl_content IS '流程定义 DSL JSON（草稿）';
COMMENT ON COLUMN flow_service_info.contract IS '服务契约 JSON：inputs/outputs/outputDescription';
COMMENT ON COLUMN flow_service_info.publish_status IS '发布状态：0=未发布, 1=已发布';
COMMENT ON COLUMN flow_service_info.published_snapshot IS '发布快照 JSON：dslContent/contract';
COMMENT ON COLUMN flow_service_info.publish_time IS '最近发布时间';
COMMENT ON COLUMN flow_service_info.info IS '服务描述';
COMMENT ON COLUMN flow_service_info.tags IS '标签，英文逗号分隔';
COMMENT ON COLUMN flow_service_info.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_service_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_service_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_service_info_create_time ON flow_service_info (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_service_info_directory_id ON flow_service_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_service_info_enabled ON flow_service_info (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_service_info_publish_status ON flow_service_info (publish_status);
