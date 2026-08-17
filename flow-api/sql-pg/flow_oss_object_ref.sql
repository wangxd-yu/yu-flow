-- Table: flow_oss_object_ref
-- 对象存储业务引用
CREATE TABLE IF NOT EXISTS flow_oss_object_ref (
  id varchar(32) NOT NULL,
  object_id varchar(32) NOT NULL,
  biz_type varchar(64) NOT NULL,
  biz_id varchar(128) NOT NULL,
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_oss_object_ref_object_id_biz_type_biz_id UNIQUE (object_id, biz_type, biz_id)
);
COMMENT ON TABLE flow_oss_object_ref IS '对象存储业务引用';
COMMENT ON COLUMN flow_oss_object_ref.id IS '雪花ID';
COMMENT ON COLUMN flow_oss_object_ref.object_id IS '台账ID';
COMMENT ON COLUMN flow_oss_object_ref.biz_type IS '业务类型';
COMMENT ON COLUMN flow_oss_object_ref.biz_id IS '业务单据ID';
COMMENT ON COLUMN flow_oss_object_ref.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_ref_object_id ON flow_oss_object_ref (object_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_ref_biz_type_biz_id ON flow_oss_object_ref (biz_type, biz_id);
