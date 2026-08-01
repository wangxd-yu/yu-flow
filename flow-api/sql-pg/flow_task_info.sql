-- Table: flow_task_info
-- 定时任务定义
CREATE TABLE IF NOT EXISTS flow_task_info (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  directory_id varchar(32),
  cron varchar(64) NOT NULL,
  enabled smallint NOT NULL DEFAULT 1,
  log_enabled smallint NOT NULL DEFAULT 1,
  log_retention_days integer,
  dsl_content text,
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
COMMENT ON TABLE flow_task_info IS '定时任务定义';
COMMENT ON COLUMN flow_task_info.id IS '雪花ID';
COMMENT ON COLUMN flow_task_info.name IS '任务名称';
COMMENT ON COLUMN flow_task_info.directory_id IS '关联目录ID（复用全局目录树）';
COMMENT ON COLUMN flow_task_info.cron IS 'Cron 表达式，如 0/5 * * * * ?';
COMMENT ON COLUMN flow_task_info.enabled IS '启用状态：0=停用, 1=启用';
COMMENT ON COLUMN flow_task_info.log_enabled IS '是否记录执行日志';
COMMENT ON COLUMN flow_task_info.log_retention_days IS '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数';
COMMENT ON COLUMN flow_task_info.dsl_content IS '流程定义 DSL JSON（草稿）';
COMMENT ON COLUMN flow_task_info.publish_status IS '发布状态：0=未发布，1=已发布';
COMMENT ON COLUMN flow_task_info.published_snapshot IS '发布快照 JSON：dslContent';
COMMENT ON COLUMN flow_task_info.publish_time IS '最近发布时间';
COMMENT ON COLUMN flow_task_info.info IS '任务描述';
COMMENT ON COLUMN flow_task_info.tags IS '标签，英文逗号分隔';
COMMENT ON COLUMN flow_task_info.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_task_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_task_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_task_info_create_time ON flow_task_info (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_task_info_directory_id ON flow_task_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_task_info_enabled ON flow_task_info (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_task_info_publish_status ON flow_task_info (publish_status);
